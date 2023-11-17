package org.sunbird.obsrv.dataproducts.job

import com.redislabs.provider.redis._
import com.typesafe.config.{Config, ConfigFactory}
import kong.unirest.Unirest
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.native.JsonMethods._
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.dataproducts.helper.MetricsHelper
import org.sunbird.obsrv.dataproducts.model.{Edata, Metric, MetricLabel}
import org.sunbird.obsrv.model.DatasetModels.{DataSource, Dataset}
import org.sunbird.obsrv.registry.DatasetRegistry
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import java.io.File
import scala.collection.mutable

object MasterDataProcessorIndexer {
  private val config: Config = ConfigFactory.load("masterdata-indexer.conf").withFallback(ConfigFactory.systemEnvironment())
  private val dayPeriodFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC()

  case class Paths(datasourceRef: String, ingestionPath: String, outputFilePath: String, timestamp: Long)

  def main(args: Array[String]): Unit = {
    val jobConfig = MasterDataIndexerConfig(config, args)
    val datasets = DatasetRegistry.getAllDatasets("master-dataset")
    val indexedDatasets = datasets.filter(dataset => {
      dataset.datasetConfig.indexData.nonEmpty && dataset.datasetConfig.indexData.get
    })
    val metrics = MetricsHelper(jobConfig)
    indexedDatasets.foreach(dataset => {
      metrics.generate(datasetId = dataset.id, edata = Edata(metric = Map(metrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", "aws"))))
      try {
        val start_time = System.currentTimeMillis()
        indexDataset(dataset, metrics, start_time)
      } catch {
        case e: Exception =>
          e.getMessage
      }
    })
  }

  def indexDataset(dataset: Dataset, metrics: MetricsHelper, time: Long): Unit = {
    try {
      val datasources = DatasetRegistry.getDatasources(dataset.id)
      if (datasources.isEmpty || datasources.get.size > 1) {
        return
      }
      val datasource = datasources.get.head
      val paths = getPaths(datasource)
      createDataFile(dataset, paths.timestamp, paths.outputFilePath, metrics)
      val ingestionSpec = updateIngestionSpec(datasource, paths.datasourceRef, paths.ingestionPath)
      submitIngestionTask(ingestionSpec)
      updateDataSourceRef(datasource, paths.datasourceRef)
      if (!datasource.datasourceRef.equals(paths.datasourceRef)) {
        deleteDataSource(datasource.datasourceRef)
      }
      val end_time = System.currentTimeMillis()
      val success_time = end_time - time
      metrics.generate(datasetId = dataset.id, edata = Edata(metric = Map(metrics.getMetricName("success_dataset_count") -> 1, metrics.getMetricName("total_time_taken") -> success_time), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", "aws"))))
    } catch {
      case e =>
        metrics.generate(datasetId = dataset.id, edata = Edata(metric = Map(metrics.getMetricName("failure_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", "aws")), err = "Failed to index dataset.", errMsg = e.getMessage))
    }
  }

  def getPaths(datasource: DataSource): Paths = {
    val dt = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay()
    val timestamp = dt.getMillis
    val date = dayPeriodFormat.print(dt)
    val cloudPrefix = if (config.getString("cloudStorage.provider") == "azure") {
      getProviderUriFormat() + s"""${config.getString("cloudStorage.accountName")}.blob.core.windows.net/${config.getString("cloudStorage.container")}/"""
    } else {
      getProviderUriFormat() + s"""/home/sankethika/${config.getString("cloudStorage.container")}/"""
    }
    val pathSuffix = s"""masterdata-indexer/${datasource.datasetId}/$date/"""
    val ingestionPath = cloudPrefix.replace(getProviderUriFormat(), getProviderFilePrefix()) + pathSuffix
    val datasourceRef = datasource.datasource + '-' + date
    val outputFilePath = cloudPrefix + pathSuffix
    Paths(datasourceRef, ingestionPath, outputFilePath, timestamp)
  }

  private def updateIngestionSpec(datasource: DataSource, datasourceRef: String, filePath: String): String = {
    val deltaIngestionSpec = s"""{"type":"index_parallel","spec":{"dataSchema":{"dataSource":"$datasourceRef"},"ioConfig":{"type":"index_parallel"},"tuningConfig":{"type":"index_parallel","maxRowsInMemory":500000,"forceExtendableShardSpecs":false,"logParseExceptions":true}}}"""
    val provider = getProvider()
    val inputSourceSpec = s"""{"spec":{"ioConfig":{"type":"index_parallel","inputSource":{"type":"$provider","objectGlob":"**.json.gz","prefixes":["$filePath"]}}}}"""
    val deltaJson = parse(deltaIngestionSpec)
    val inputSourceJson = parse(inputSourceSpec)
    val ingestionSpec = parse(datasource.ingestionSpec)
    val modIngestionSpec = ingestionSpec merge deltaJson merge inputSourceJson
    compact(render(modIngestionSpec))
  }

  @throws[Exception]
  private def getProviderUriFormat(): String = {
    config.getString("cloudStorage.provider") match {
      case "local" => "file:///"
      case "aws" => "s3a://"
      case "azure" => "wasbs://"
      case "gcloud" => "gs://"
      case "cephs3" => "s3a://" // TODO: Have to check Druid compatibility
      case "oci" => "s3a://" // TODO: Have to check Druid compatibility
      case _ => throw new Exception("Unsupported provider")
    }
  }

  @throws[Exception]
  private def getProviderFilePrefix(): String = {
    config.getString("cloudStorage.provider") match {
      case "local" => "file"
      case "aws" => "s3"
      case "azure" => "azure"
      case "gcloud" => "gs"
      case "cephs3" => "s3" // TODO: Have to check Druid compatibility
      case "oci" => "s3" // TODO: Have to check Druid compatibility
      case _ => throw new Exception("Unsupported provider")
    }
  }

  @throws[Exception]
  private def getProvider(): String = {
    config.getString("cloudStorage.provider") match {
      case "local" => "file"
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
    val response = Unirest.post(config.getString("druid.indexer.url"))
      .header("Content-Type", "application/json")
      .body(ingestionSpec).asJson()
    response.ifFailure(response => throw new Exception("Exception while submitting ingestion task"))
  }

  private def updateDataSourceRef(datasource: DataSource, datasourceRef: String): Unit = {
    DatasetRegistry.updateDatasourceRef(datasource, datasourceRef)
  }

  private def deleteDataSource(datasourceRef: String): Unit = {
    // TODO: Handle success and failure responses properly
    val response = Unirest.delete(config.getString("druid.datasource.delete.url") + datasourceRef)
      .header("Content-Type", "application/json")
      .asJson()
    response.ifFailure(response => throw new Exception("Exception while deleting datasource" + datasourceRef))
  }

  def createDataFile(dataset: Dataset, timestamp: Long, outputFilePath: String, metrics: MetricsHelper): Unit = {
    val conf = new SparkConf()
      .setAppName("MasterDataProcessorIndexer")
      .set("spark.redis.host", dataset.datasetConfig.redisDBHost.get)
      .set("spark.redis.port", String.valueOf(dataset.datasetConfig.redisDBPort.get))
      .set("spark.redis.db", String.valueOf(dataset.datasetConfig.redisDB.get))
    val readWriteConf = ReadWriteConfig(scanCount = 1000, maxPipelineSize = 1000)
    val sc = new SparkContext(conf)
    val spark = new SparkSession.Builder().config(conf).getOrCreate()
    val rdd = sc.fromRedisKV("*")(readWriteConfig = readWriteConf)
      .map(f =>
        processEvent(f._2, timestamp)
      )
    val response = rdd.collect()
    val stringifiedResponse = JSONUtil.serialize(response)
    val df = spark.read.json(spark.sparkContext.parallelize(Seq(stringifiedResponse)))
    println("Dataset - " + dataset.id + " No. of records - " + df.count())
    println("Writing to file - " + outputFilePath)
    df.coalesce(1).write.mode("overwrite").option("compression", "gzip").json(outputFilePath)
    println("path - " + outputFilePath)
    metrics.generate(datasetId = dataset.id, edata = Edata(metric = Map(metrics.getMetricName("total_events_processed") -> df.count()), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.id), MetricLabel("cloud", "aws"))))
    spark.stop()
    sc.stop()
  }

  private def processEvent(value: String, timestamp: Long) = {
    val json = JSONUtil.deserialize[mutable.Map[String, AnyRef]](value)
    json("obsrv_meta") = mutable.Map[String, AnyRef]("syncts" -> timestamp.asInstanceOf[AnyRef]).asInstanceOf[AnyRef]
    json
  }
}