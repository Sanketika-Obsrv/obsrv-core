package org.sunbird.obsrv.dataproducts

import com.redislabs.provider.redis._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.logging.log4j.{LogManager, Logger}
import org.apache.spark.sql.SparkSession
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.native.JsonMethods._
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.dataproducts.helper.BaseMetricHelper
import org.sunbird.obsrv.dataproducts.model.{Edata, MetricLabel}
import org.sunbird.obsrv.dataproducts.util.{CommonUtil, HttpUtil, StorageUtil}
import org.sunbird.obsrv.model.DatasetModels.{DataSource, Dataset}
import org.sunbird.obsrv.model.DatasetStatus
import org.sunbird.obsrv.registry.DatasetRegistry

object MasterDataProcessorIndexer {
  private final val logger: Logger = LogManager.getLogger(MasterDataProcessorIndexer.getClass)
  val httpUtil = new HttpUtil()

  @throws[ObsrvException]
  def processDataset(config: Config, dataset: Dataset, spark: SparkSession): Map[String, Long] = {

    val result = CommonUtil.time {
      val datasource = fetchDatasource(dataset)
      val paths = StorageUtil.getPaths(datasource, config)
      val eventsCount = createDataFile(dataset, paths.outputFilePath, spark, config)
      val ingestionSpec = updateIngestionSpec(datasource, paths.datasourceRef, paths.ingestionPath, config)
      if (eventsCount > 0L) {
        submitIngestionTask(dataset.id, ingestionSpec, config)
      }
      DatasetRegistry.updateDatasourceRef(datasource, paths.datasourceRef)
      if (!datasource.datasourceRef.equals(paths.datasourceRef)) {
        deleteDataSource(dataset.id, datasource.datasourceRef, config)
      }
      Map("success_dataset_count" -> 1, "total_dataset_count" -> 1, "total_events_processed" -> eventsCount)
    }

    val metricMap = result._2 ++ Map("total_time_taken" -> result._1)
    metricMap.asInstanceOf[Map[String, Long]]
  }

  private def updateIngestionSpec(datasource: DataSource, datasourceRef: String, filePath: String, config: Config): String = {
    val deltaIngestionSpec = config.getString("delta_ingestion_spec").replace("DATASOURCE_REF", datasourceRef)
    val inputSourceSpec = StorageUtil.inputSourceSpecProvider(filePath, config)
    val deltaJson = parse(deltaIngestionSpec)
    val inputSourceJson = parse(inputSourceSpec)
    val ingestionSpec = parse(datasource.ingestionSpec)
    val modIngestionSpec = ingestionSpec merge deltaJson merge inputSourceJson
    compact(render(modIngestionSpec))
  }

  private def submitIngestionTask(datasetId: String, ingestionSpec: String, config: Config) = {
    logger.debug(s"submitIngestionTask() | datasetId=$datasetId")
    httpUtil.post(config.getString("druid.indexer.url"), ingestionSpec)
  }

  private def deleteDataSource(datasetID: String, datasourceRef: String, config: Config): Unit = {
    logger.debug(s"deleteDataSourc() | datasetId=$datasetID")
    httpUtil.delete(config.getString("druid.datasource.delete.url") + datasourceRef)
  }

  def createDataFile(dataset: Dataset, outputFilePath: String, spark: SparkSession, config: Config): Long = {
    try {
      logger.info(s"createDataFile() | START | dataset=${dataset.id} ")
      import spark.implicits._
      val readWriteConf = ReadWriteConfig(scanCount = config.getInt("redis_scan_count"), maxPipelineSize = config.getInt("redis_maxPipelineSize"))
      val redisConfig = new RedisConfig(initialHost = RedisEndpoint(host = dataset.datasetConfig.redisDBHost.get, port = dataset.datasetConfig.redisDBPort.get, dbNum = dataset.datasetConfig.redisDB.get))
      val ts = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay().getMillis
      val rdd = spark.sparkContext.fromRedisKV("*")(redisConfig = redisConfig, readWriteConfig = readWriteConf).map(
        f => CommonUtil.processEvent(f._2, ts)
      )
      val noOfRecords = rdd.count()
      if (noOfRecords > 0) {
        rdd.toDF().write.mode("overwrite").option("compression", "gzip").json(outputFilePath)
      }
      logger.info(s"createDataFile() | END | dataset=${dataset.id} | noOfRecords=$noOfRecords")
      noOfRecords
    } catch {
      case ex: Exception =>
        logger.error(s"createDataset() | FAILED | datasetId=${dataset.id} | Error=${ex.getMessage}", ex)
        throw new ObsrvException(ErrorConstants.CLOUD_PROVIDER_CONFIG_ERR)
    }
  }

  private def getDatasets(): List[Dataset] = {
    val datasets = DatasetRegistry.getAllDatasets("master-dataset")
    datasets.filter(dataset => {
      dataset.datasetConfig.indexData.nonEmpty && dataset.datasetConfig.indexData.get && dataset.status == DatasetStatus.Live
    })
  }

  def fetchDatasource(dataset: Dataset): DataSource = {
      val datasources = DatasetRegistry.getDatasources(dataset.id).get
      if(datasources.isEmpty) {
        throw new ObsrvException(ErrorConstants.ERR_DATASOURCE_NOT_FOUND)
      }
      datasources.head
  }

  def processDatasets(config: Config, spark: SparkSession): Unit = {

    val datasets = getDatasets()
    val metricHelper = BaseMetricHelper(config)
    datasets.foreach(dataset => {
      logger.info(s"processDataset() | START | datasetId=${dataset.id}")
      val metricData = try {
        val metrics = processDataset(config, dataset, spark)
        logger.info(s"processDataset() | SUCCESS | datasetId=${dataset.id} | Metrics=$metrics")
        Edata(metric = metrics, labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", s"${config.getString("cloudStorage.provider")}")))
      } catch {
        case ex: ObsrvException =>
          logger.error(s"processDataset() | FAILED | datasetId=${dataset.id} | Error=${ex.error}", ex)
          Edata(metric = Map(metricHelper.getMetricName("failure_dataset_count") -> 1, "total_dataset_count" -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", s"${config.getString("cloudStorage.provider")}")), err = ex.error.errorCode, errMsg = ex.error.errorMsg)
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