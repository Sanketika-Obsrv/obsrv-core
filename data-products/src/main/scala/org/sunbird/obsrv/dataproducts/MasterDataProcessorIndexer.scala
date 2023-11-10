package org.sunbird.obsrv.dataproducts

import com.codahale.metrics.{Counter, MetricRegistry}
import com.redislabs.provider.redis._
import com.typesafe.config.{Config, ConfigFactory}
import kong.unirest.Unirest
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}
import org.sunbird.obsrv.model.DatasetModels.{DataSource, Dataset}
import org.sunbird.obsrv.registry.DatasetRegistry
import org.json4s.native.JsonMethods._

import java.io.File
import scala.collection.mutable
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, DefaultCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import org.sunbird.obsrv.core.util.JSONUtil
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import java.nio.file.Files
import scala.util.{Failure, Success, Try}

object MasterDataProcessorIndexer {

  private val config: Config = ConfigFactory.load("masterdata-indexer.conf").withFallback(ConfigFactory.systemEnvironment())
  private val dayPeriodFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC()
  private final val logger: Logger = LogManager.getLogger(MasterDataProcessorIndexer.getClass)
  private val prefix = config.getString("prefix")

  private case class Paths(datasourceRef: String, objectKey: String, outputFilePath: String, timestamp: Long)

  private val metricRegistry: MetricRegistry = new MetricRegistry
  private val totalDatasetCount: Counter = metricRegistry.counter(prefix + "TOTAL_DATASET_COUNT")
  private val successDatasetCount: Counter = metricRegistry.counter(prefix + "SUCCESS_DATASET_COUNT")
  private val failedDatasetCount: Counter = metricRegistry.counter(prefix + "FAILED_DATASET_COUNT")
  private val totalEventsProcessed: Counter = metricRegistry.counter(prefix + "TOTAL_EVENTS_PROCESSED")
  private val successEventsPerDataset: Counter = metricRegistry.counter(prefix + "SUCCESS_EVENTS_PER_DATASET")
  private val failedEventPerDataset: Counter = metricRegistry.counter(prefix + "FAILED_EVENTS_PER_DATASET")

  def main(args: Array[String]): Unit = {
    val datasets = DatasetRegistry.getAllDatasets("master-dataset")
    val indexedDatasets = datasets.filter(dataset => {
      dataset.datasetConfig.indexData.nonEmpty && dataset.datasetConfig.indexData.get
    })
    indexedDatasets.foreach(dataset => {
      try {
        totalDatasetCount.inc()
        successDatasetCount.inc()
        indexDataset(dataset)
      } catch {
        case e: Exception =>
          failedDatasetCount.inc()
          logger.debug("Failed dataset: " + failedDatasetCount.getCount)
          e.printStackTrace()
      }
    })
  }

  private def indexDataset(dataset: Dataset): Unit = {

    val datasources = DatasetRegistry.getDatasources(dataset.id)
    if (datasources.isEmpty || datasources.get.size > 1) {
      return
    }
    val datasource = datasources.get.head
    val paths = getPaths(datasource)
    createDataFile(dataset, paths.timestamp, paths.outputFilePath, paths.objectKey)
    val ingestionSpec = updateIngestionSpec(datasource, paths.datasourceRef, paths.objectKey)
    submitIngestionTask(ingestionSpec)
    updateDataSourceRef(datasource, paths.datasourceRef)
    if (!datasource.datasource.equals(datasource.datasourceRef)) {
      deleteDataSource(datasource.datasourceRef)
    }
  }

  private def getPaths(datasource: DataSource): Paths = {
    val dt = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay()
    val timestamp = dt.getMillis
    val date = dayPeriodFormat.print(dt)
    val objectKey = "masterdata-indexer/" + datasource.datasetId + "/" + date + ".json.gz"
    val datasourceRef = datasource.datasource + '-' + date
    val outputFilePath = "masterdata-indexer/" + datasource.datasetId + "/" + date
    Paths(datasourceRef, objectKey, outputFilePath, timestamp)
  }

  private def updateIngestionSpec(datasource: DataSource, datasourceRef: String, objectKey: String): String = {
    val deltaIngestionSpec = s"""{"type":"index_parallel","spec":{"dataSchema":{"dataSource":"$datasourceRef"},"ioConfig":{"type":"index_parallel"},"tuningConfig":{"type":"index_parallel","maxRowsInMemory":25000,"forceExtendableShardSpecs":false,"logParseExceptions":true}}}"""
    val provider = getProvider()
    val container = config.getString("cloudStorage.container")
    val inputSourceSpec = s"""{"spec":{"ioConfig":{"inputSource":{"type":"$provider","objectGlob":"**.json.gz","objects":[{"bucket":"$container","path":"$objectKey"}]}}}}"""
    val deltaJson = parse(deltaIngestionSpec)
    val inputSourceJson = parse(inputSourceSpec)
    val ingestionSpec = parse(datasource.ingestionSpec)
    val modIngestionSpec = ingestionSpec merge deltaJson merge inputSourceJson
    compact(render(modIngestionSpec))
  }

  @throws[Exception]
  private def getProvider(): String = {
    config.getString("cloudStorage.provider") match {
      case "aws" => "s3"
      case "azure" => "azure"
      case "gcloud" => "google"
      case "cephs3" => "s3" // TODO: Have to check Druid compatibility
      case "oci" => "s3" // TODO: Have to check Druid compatibility
      case _ => throw new Exception("Unsupported provider")
    }
  }

  private def upload(cloudProvider: String) = {
    cloudProvider match {
      case "aws" =>
        (bucketName: String, key: String, payload: File) => {

          val s3Client = S3Client.builder()
            .region(Region.US_EAST_2)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()

          val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("application/json")
            .build()

          val jsonPayload = RequestBody.fromFile(payload)
          s3Client.putObject(request, jsonPayload)
        }
      case _ => throw new Exception("Unsupported provider")
    }
  }

  private def submitIngestionTask(ingestionSpec: String) = {
    // TODO: Handle success and failure responses properly
    val response = Unirest.post(config.getString("druid_indexer_url"))
      .header("Content-Type", "application/json")
      .body(ingestionSpec).asJson()
    response.ifFailure(response => throw new Exception("Exception while submitting ingestion task"))
  }

  private def updateDataSourceRef(datasource: DataSource, datasourceRef: String): Unit = {
    DatasetRegistry.updateDatasourceRef(datasource, datasourceRef)
  }

  private def deleteDataSource(datasourceRef: String): Unit = {
    // TODO: Handle success and failure responses properly
    val response = Unirest.delete(config.getString("druid_datasource_delete_url") + datasourceRef)
      .header("Content-Type", "application/json")
      .asJson()
    response.ifFailure(response => throw new Exception("Exception while deleting datasource" + datasourceRef))
  }

  private def createDataFile(dataset: Dataset, timestamp: Long, outputFilePath: String, objectKey: String) = {
    cleanDirectory(outputFilePath)
    val conf = new SparkConf()
      .setAppName("MasterDataProcessorIndexer")
      .set("spark.redis.host", dataset.datasetConfig.redisDBHost.get)
      .set("spark.redis.port", String.valueOf(dataset.datasetConfig.redisDBPort.get))
      .set("spark.redis.db", String.valueOf(dataset.datasetConfig.redisDB.get))
    val readWriteConf = ReadWriteConfig(scanCount = 1000, maxPipelineSize = 1000)
    val sc = new SparkContext(conf)
    val rdd = sc.fromRedisKV("*")(readWriteConfig = readWriteConf)
      .map(f =>
        processEvent(f._2, timestamp, dataset)
      ).coalesce(1)
    val response = rdd.collect()
    val stringifiedResponse = JSONUtil.serialize(response)
    val spark = new SparkSession.Builder().config(conf).getOrCreate()
    val df = spark.read.json(spark.sparkContext.parallelize(Seq(stringifiedResponse)))
    df.coalesce(1).write.mode("overwrite").option("compression", "gzip").format("json").save(outputFilePath)
    val dt = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay()
    val date = dayPeriodFormat.print(dt)
    val target = new File(s"/home/sankethika/Downloads/masterdata-indexer/telemetry-content-data/${date}.json.gz")
    val srcDir = new File(s"/home/sankethika/Downloads/masterdata-indexer/telemetry-content-data/${date}/")
    val files = srcDir.listFiles()
    files.foreach(file =>
      if (file.getName.endsWith(".json.gz")) {
        cleanDirectory(target.toString)
        Files.move(file.toPath, target.toPath)
        println("File moved")
      }
    )
    spark.stop()
    sc.stop()
    upload(config.getString("cloudStorage.provider"))(config.getString("cloudStorage.container"), objectKey, target)
  }

  private def processEvent(value: String, timestamp: Long, dataset: Dataset) = {
    try {
      val json = JSONUtil.deserialize[mutable.Map[String, AnyRef]](value)
      json("obsrv_meta") = mutable.Map[String, AnyRef]("syncts" -> timestamp.asInstanceOf[AnyRef]).asInstanceOf[AnyRef]
      totalEventsProcessed.inc()
      successEventsPerDataset.inc()
      logger.debug(s"Total events processed for ${dataset.id}, count -> ${successEventsPerDataset.getCount}")
      json
    } catch {
      case e: Exception =>
        failedEventPerDataset.inc()
        logger.debug(s"Failed to process event for ${dataset.id}, count -> ${failedEventPerDataset.getCount}")
        logger.error(s"Exception while processing event: ${e.printStackTrace()}")
    }
  }

  private def cleanDirectory(dir: String): Unit = {
    if (java.nio.file.Files.exists(java.nio.file.Paths.get(dir))) {
      val directoryPath: java.nio.file.Path = java.nio.file.Paths.get(dir)
      java.nio.file.Files.walk(directoryPath)
        .sorted(java.util.Comparator.reverseOrder()) // Start from deepest files and directories
        .forEach { path =>
          java.nio.file.Files.delete(path)
        }
    } else {
      println("Path doesn't exists" + dir)
    }
  }
}


