package org.sunbird.obsrv.dataproducts

import com.redislabs.provider.redis._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.logging.log4j.{LogManager, Logger}
import org.apache.spark.sql.SparkSession
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.native.JsonMethods._
import org.json4s._
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.dataproducts.helper.BaseMetricHelper
import org.sunbird.obsrv.dataproducts.model.{Edata, MetricLabel}
import org.sunbird.obsrv.dataproducts.util.{CommonUtil, HttpUtil, StorageUtil}
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.model.DatasetStatus
import org.sunbird.obsrv.registry.DatasetRegistry

object MasterDataProcessorIndexer {
  private final val logger: Logger = LogManager.getLogger(MasterDataProcessorIndexer.getClass)

  @throws[ObsrvException]
  def processDataset(config: Config, dataset: Dataset, spark: SparkSession): Map[String, Long] = {
    val result = CommonUtil.time {
      val paths = StorageUtil.getPaths(dataset, config)
      val eventsCount: Long = createDataFile(dataset, paths.outputFilePath, spark, config)
      val ingestionSpec: String = updateIngestionSpec(paths.datasourceRef, paths.ingestionPath, config)
      if (eventsCount > 0L) {
        // Full snapshot re-indexed into a stable datasource_ref. dropExisting + MONTH granularity
        // over the current-month interval makes Druid atomically replace the month segment, so the
        // run fully replaces the content without a manual segment delete (a failed task leaves the
        // existing data intact — no data gap).
        submitIngestionTask(dataset.id, ingestionSpec, config)
        createOrUpdateDatasource(dataset, paths.datasourceRef, ingestionSpec)
      }
      Map("success_dataset_count" -> 1, "total_dataset_count" -> 1, "total_events_processed" -> eventsCount)
    }
    val metricMap = result._2 ++ Map("total_time_taken" -> result._1)
    metricMap.asInstanceOf[Map[String, Long]]
  }

  // This method is used to update the ingestion spec based on datasource ref and storage path
  def updateIngestionSpec(datasourceRef: String, filePath: String, config: Config): String = {
    val deltaIngestionSpec: String = config.getString("delta.ingestion.spec").replace("DATASOURCE_REF", datasourceRef)
    val inputSourceSpec: String = StorageUtil.getInputSourceSpec(filePath, config)
    // Master data is a full snapshot: replace-in-place each run. Inject the configured
    // segmentGranularity (defaults to MONTH) + the current-month interval (required by dropExisting)
    // and dropExisting itself, so the spec is correct regardless of what the base conf carries.
    // appendToExisting defaults to false in Druid (and dropExisting requires it false), so it is
    // left implicit.
    val segmentGranularity: String = if (config.hasPath("druid.segment.granularity")) config.getString("druid.segment.granularity") else "MONTH"
    val replaceSpec: String = s"""{"spec":{"dataSchema":{"granularitySpec":{"type":"uniform","segmentGranularity":"$segmentGranularity","intervals":["${StorageUtil.getIngestionInterval}"]}},"ioConfig":{"type":"index_parallel","dropExisting":true}}}"""
    val deltaJson = parse(deltaIngestionSpec)
    val inputSourceJson = parse(inputSourceSpec)
    val replaceJson = parse(replaceSpec)
    val modIngestionSpec = deltaJson merge replaceJson merge inputSourceJson
    compact(render(modIngestionSpec))
  }

  // Register the master dataset's Druid datasource in the datasources table so the data-out
  // query path can resolve it. Uses the same naming as a normal dataset's primary datasource:
  // id = <datasetId>_events_druid, datasource (alias) = <datasetId>_druid, datasource_ref =
  // <datasetId>_events. The ref is stable (no date suffix) — the daily re-index replaces data
  // in place via dropExisting — so a single Live row is created once and reused.
  def createOrUpdateDatasource(dataset: Dataset, datasourceRef: String, ingestionSpec: String): Unit = {
    val datasourceName = s"${dataset.id}_druid"
    val datasourceId = s"${dataset.id}_events_druid"
    val metadata = """{"aggregated":false,"granularity":"day"}"""
    val existing = DatasetRegistry.getDatasources(dataset.id).getOrElse(List())
    val liveDatasource = existing.find(ds => ds.datasource == datasourceName && ds.status == "Live")
    liveDatasource match {
      case Some(_) =>
        logger.info(s"createOrUpdateDatasource() | datasetId=${dataset.id} | datasource=$datasourceName already Live, skipping")
      case None =>
        DatasetRegistry.insertDatasource(datasourceId, dataset.id, datasourceName, datasourceRef, ingestionSpec, metadata = metadata, isPrimary = true)
        logger.info(s"createOrUpdateDatasource() | datasetId=${dataset.id} | created datasource=$datasourceName | datasourceRef=$datasourceRef")
    }
  }

  // This method is used to submit the ingestion task to Druid for indexing data
  def submitIngestionTask(datasetId: String, ingestionSpec: String, config: Config): Unit = {
    logger.debug(s"submitIngestionTask() | datasetId=$datasetId")
    val headers = druidAuthHeaders(config)
    val response = HttpUtil.post(config.getString("druid.indexer.url"), ingestionSpec, headers)
    logger.info(s"submitIngestionTask() | status=${response.getStatus} | body=${response.getBody}")
    if (!response.isSuccess) throw new ObsrvException(ErrorConstants.ERR_SUBMIT_INGESTION_FAILED)
  }

  private def druidAuthHeaders(config: Config): Map[String, String] = {
    if (config.hasPath("druid.username") && config.hasPath("druid.password")) {
      val credentials = java.util.Base64.getEncoder.encodeToString(
        s"${config.getString("druid.username")}:${config.getString("druid.password")}".getBytes
      )
      Map("Content-Type" -> "application/json", "Authorization" -> s"Basic $credentials")
    } else {
      Map("Content-Type" -> "application/json")
    }
  }

  // This method will fetch the data from redis based on dataset config
  // then write the data as a compressed JSON to the respective cloud provider
  private def createDataFile(dataset: Dataset, outputFilePath: String, spark: SparkSession, config: Config): Long = {
    logger.info(s"createDataFile() | START | dataset=${dataset.id} ")
    val readWriteConf = ReadWriteConfig(scanCount = config.getInt("redis.scan.count"), maxPipelineSize = config.getInt("redis.max.pipeline.size"))
    val cacheConfig = dataset.datasetConfig.cacheConfig.get
    val redisConfig = new RedisConfig(initialHost = RedisEndpoint(host = cacheConfig.redisDBHost.get, port = cacheConfig.redisDBPort.get, dbNum = cacheConfig.redisDB.get))
    val ts: Long = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay().getMillis
    val rdd = spark.sparkContext.fromRedisKV("*")(redisConfig = redisConfig, readWriteConfig = readWriteConf).map(
      f => CommonUtil.processEvent(f._2, ts)
    )
    val noOfRecords: Long = rdd.count()
    if (noOfRecords > 0) {
      spark.read.json(rdd).write.mode("overwrite").option("compression", "gzip").json(outputFilePath)
    }
    logger.info(s"createDataFile() | END | dataset=${dataset.id} | noOfRecords=$noOfRecords")
    noOfRecords
  }

  private def getDatasets(): List[Dataset] = {
    val datasets: List[Dataset] = DatasetRegistry.getAllDatasets(Some("master"))
    datasets.filter(dataset => {
      dataset.status == DatasetStatus.Live
    })
  }

  // This method will fetch the dataset from database and processes the dataset
  // then generates required metrics
  def processDatasets(config: Config, spark: SparkSession): Unit = {
    val datasets: List[Dataset] = getDatasets()
    val metricHelper = new BaseMetricHelper(config)
    datasets.foreach(dataset => {
      logger.info(s"processDataset() | START | datasetId=${dataset.id}")
      val metricData = try {
        val metrics = processDataset(config, dataset, spark)
        logger.info(s"processDataset() | SUCCESS | datasetId=${dataset.id} | Metrics=$metrics")
        Edata(metric = metrics, labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", s"${config.getString("cloud.storage.provider")}")))
      } catch {
        case ex: ObsrvException =>
          logger.error(s"processDataset() | FAILED | datasetId=${dataset.id} | Error=${ex.error}", ex)
          Edata(metric = Map(metricHelper.getMetricName("failure_dataset_count") -> 1, "total_dataset_count" -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", s"${config.getString("cloud.storage.provider")}")), err = ex.error.errorCode, errMsg = ex.error.errorMsg)
      }
      metricHelper.generate(datasetId = dataset.id, edata = metricData)
    })
  }

  // $COVERAGE-OFF$
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("masterdata-indexer.conf").withFallback(ConfigFactory.systemEnvironment())
    val spark = CommonUtil.getSparkSession("MasterDataIndexer", config)
    processDatasets(config, spark)
    spark.stop()
  }
  // $COVERAGE-ON$
}