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
  private final val defaultRetentionPeriodInDays: Int = 2

  @throws[ObsrvException]
  def processDataset(config: Config, dataset: Dataset, spark: SparkSession): Map[String, Long] = {
    val result = CommonUtil.time {
      val paths = StorageUtil.getPaths(dataset, config)
      val eventsCount: Long = createDataFile(dataset, paths.outputFilePath, spark, config)
      val ingestionSpec: String = updateIngestionSpec(paths.datasourceRef, paths.ingestionPath, config)
      if (eventsCount > 0L) {
        submitIngestionTask(dataset.id, ingestionSpec, config)
        createOrUpdateDatasource(dataset, paths.datasourceRef, ingestionSpec)
      }
      val retentionPeriodInDays: Int =
        if (config.hasPath("datasource.retention.period.days") && config.getInt("datasource.retention.period.days") != 0)
          config.getInt("datasource.retention.period.days")
        else
          defaultRetentionPeriodInDays

      val unusedDataSource: String = StorageUtil.getDataSourceRefFormat(dataset, StorageUtil.getDate(retentionPeriodInDays))
      if (!unusedDataSource.equals(paths.datasourceRef)) {
        deleteDataSource(dataset.id, unusedDataSource, config)
      }
      Map("success_dataset_count" -> 1, "total_dataset_count" -> 1, "total_events_processed" -> eventsCount)
    }
    val metricMap = result._2 ++ Map("total_time_taken" -> result._1)
    metricMap.asInstanceOf[Map[String, Long]]
  }

  // This method is used to update the ingestion spec based on datasource ref and storage path
  private def updateIngestionSpec(datasourceRef: String, filePath: String, config: Config): String = {
    val deltaIngestionSpec: String = config.getString("delta.ingestion.spec").replace("DATASOURCE_REF", datasourceRef)
    val inputSourceSpec: String = StorageUtil.getInputSourceSpec(filePath, config)
    val deltaJson = parse(deltaIngestionSpec)
    val inputSourceJson = parse(inputSourceSpec)
    val modIngestionSpec = deltaJson merge inputSourceJson
    compact(render(modIngestionSpec))
  }

  // After the ingestion spec is submitted to Druid, reflect the live datasource_ref in the
  // datasources table so the data-out query path can resolve and verify the master datasource.
  // Keeps a single Live row (datasource = <datasetId>_druid) pointing at the current dated ref;
  // the previous Live row is retired (status = Retired, datasource renamed to its own ref).
  def createOrUpdateDatasource(dataset: Dataset, datasourceRef: String, ingestionSpec: String): Unit = {
    val datasourceName = s"${dataset.id}_druid_master"
    val existing = DatasetRegistry.getDatasources(dataset.id).getOrElse(List())
    val liveDatasource = existing.find(ds => ds.datasource == datasourceName && ds.status == "Live")
    liveDatasource match {
      case Some(ds) if ds.datasourceRef == datasourceRef =>
        logger.info(s"createOrUpdateDatasource() | datasetId=${dataset.id} | datasourceRef=$datasourceRef already Live, skipping")
      case Some(ds) =>
        DatasetRegistry.retireAndInsertDatasource(ds.id, ds.datasourceRef, dataset.id, datasourceName, datasourceRef, ingestionSpec, isPrimary = true)
        logger.info(s"createOrUpdateDatasource() | datasetId=${dataset.id} | retired=${ds.id} | new datasourceRef=$datasourceRef")
      case None =>
        DatasetRegistry.insertDatasource(dataset.id, datasourceName, datasourceRef, ingestionSpec, isPrimary = true)
        logger.info(s"createOrUpdateDatasource() | datasetId=${dataset.id} | created datasourceRef=$datasourceRef")
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

  // This method is used for deleting a datasource from druid
  private def deleteDataSource(datasetID: String, datasourceRef: String, config: Config): Unit = {
    logger.debug(s"deleteDataSource() | datasetId=$datasetID")
    val url = config.getString("druid.datasource.delete.url") + datasourceRef
    val response = HttpUtil.delete(url, druidAuthHeaders(config))
    if (!response.isSuccess) throw new ObsrvException(ErrorConstants.ERR_DELETE_DATASOURCE_FAILED)
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