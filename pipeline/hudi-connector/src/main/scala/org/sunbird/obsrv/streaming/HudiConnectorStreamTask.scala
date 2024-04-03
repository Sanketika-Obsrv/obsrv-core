package org.sunbird.obsrv.streaming

import com.typesafe.config.ConfigFactory
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.hudi.configuration.FlinkOptions
import org.apache.hudi.sink.utils.Pipelines
import org.apache.hudi.util.AvroSchemaConverter
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.streaming.{BaseStreamTask, FlinkKafkaConnector}
import org.sunbird.obsrv.core.util.FlinkUtil
import org.sunbird.obsrv.functions.RowDataConverterFunction
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.util.HudiSchemaParser

import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable.{Map => MMap}

class HudiConnectorStreamTask(config: HudiConnectorConfig, kafkaConnector: FlinkKafkaConnector) extends BaseStreamTask[String] {

  implicit val mutableMapTypeInfo: TypeInformation[MMap[String, AnyRef]] = TypeExtractor.getForClass(classOf[MMap[String, AnyRef]])
  private val logger = LoggerFactory.getLogger(classOf[HudiConnectorStreamTask])
  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    process(env)
    env.execute(config.jobName)
  }

  override def processStream(dataStream: DataStream[String]): DataStream[String] = {
    null
  }

  def process(env: StreamExecutionEnvironment): Unit = {
    val schemaParser = new HudiSchemaParser()
    val dataSourceConfig = DatasetRegistry.getAllDatasources().filter(f => f.datalakeIngestionSpec.nonEmpty)
    dataSourceConfig.map{ dataSource =>
      val dataStream = getMapDataStream(env, config, kafkaConnector)
        .map(new RowDataConverterFunction())
      val datasetId = dataSource.datasetId
      val conf: Configuration = new Configuration()
      setHudiBaseConfigurations(conf)
      setDatasetConf(conf, datasetId, schemaParser)
      val rowType = schemaParser.rowTypeMap(datasetId)
      Pipelines.append(conf, rowType, dataStream)
    }
    env.execute("Flink-Hudi-Connector")
  }

  def setDatasetConf(conf: Configuration, dataset: String, schemaParser: HudiSchemaParser): Unit = {
    val datasetSchema = schemaParser.hudiSchemaMap(dataset)
    val rowType = schemaParser.rowTypeMap(dataset)
    val avroSchema = AvroSchemaConverter.convertToSchema(rowType, dataset)
    conf.setString(FlinkOptions.PATH.key, s"${config.hudiBasePath}/${datasetSchema.schema.table}")
    conf.setString(FlinkOptions.TABLE_NAME, datasetSchema.schema.table)
    conf.setString(FlinkOptions.RECORD_KEY_FIELD.key, datasetSchema.schema.primaryKey)
    conf.setString(FlinkOptions.PRECOMBINE_FIELD.key, datasetSchema.schema.timestampColumn)
    conf.setString(FlinkOptions.PARTITION_PATH_FIELD.key, datasetSchema.schema.partitionColumn)
    conf.setString(FlinkOptions.SOURCE_AVRO_SCHEMA.key, avroSchema.toString)

    if (config.hmsEnabled) {
      conf.setString("hive_sync.table", datasetSchema.schema.table)
    }
  }

  /*
  private def createRowType(schema: HudiSchemaSpec): RowType = {
    val columnSpec = schema.schema.columnSpec
    val primaryKey = schema.schema.primaryKey
    val partitionColumn = schema.schema.partitionColumn
    val timeStampColumn = schema.schema.timestampColumn
    val rowTypeMap = mutable.SortedMap[String, LogicalType]()
    columnSpec.sortBy(_.column).map {
      spec =>
        val isNullable = if (spec.column.matches(s"$primaryKey|$partitionColumn|$timeStampColumn")) false else true
        val columnType = spec.`type` match {
          case "string" => new VarCharType(isNullable, 20)
          case "double" => new DoubleType(isNullable)
          case "long" => new BigIntType(isNullable)
          case "int" => new IntType(isNullable)
          case "boolean" => new BooleanType(true)
          case "map[string, string]" => new MapType(new VarCharType(), new VarCharType())
          case _ => new VarCharType(isNullable, 20)
        }
        rowTypeMap.put(spec.column, columnType)
    }
    val rowType: RowType = RowType.of(false, rowTypeMap.values.toArray, rowTypeMap.keySet.toArray)
    rowType
  }
  */

  private def setHudiBaseConfigurations(conf: Configuration): Unit = {
    conf.setString(FlinkOptions.TABLE_TYPE.key, config.hudiTableType)
    conf.setBoolean(FlinkOptions.METADATA_ENABLED.key, true)
    conf.setDouble(FlinkOptions.WRITE_BATCH_SIZE.key, 0.1)
    conf.setBoolean(FlinkOptions.COMPACTION_SCHEDULE_ENABLED.key, config.hudiCompactionEnabled)
    conf.setInteger("write.tasks", config.hudiWriteTasks)
    conf.setString("hoodie.fs.atomic_creation.support", "s3a")

    if (config.hmsEnabled) {
      conf.setBoolean("hive_sync.enabled", config.hmsEnabled)
      conf.setString(FlinkOptions.HIVE_SYNC_DB.key(), config.hmsDatabaseName)
      conf.setString("hive_sync.username", config.hmsUsername)
      conf.setString("hive_sync.password", config.hmsPassword)
      conf.setString("hive_sync.mode", "hms")
      conf.setBoolean("hive_sync.use_jdbc", false)
      conf.setString(FlinkOptions.HIVE_SYNC_METASTORE_URIS.key(), config.hmsURI)
      conf.setString("hoodie.fs.atomic_creation.support", "s3a")
    }

  }

}

object HudiConnectorStreamTask {
  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("hudi-writer.conf").withFallback(ConfigFactory.systemEnvironment()))
    val hudiWriterConfig = new HudiConnectorConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(hudiWriterConfig)
    val task = new HudiConnectorStreamTask(hudiWriterConfig, kafkaUtil)
    task.process()
  }

  def getTimestamp(ts: String): Timestamp = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val localDateTime = if (StringUtils.isNotBlank(ts))
      LocalDateTime.from(formatter.parse(ts))
    else LocalDateTime.now
    Timestamp.valueOf(localDateTime)
  }
}
