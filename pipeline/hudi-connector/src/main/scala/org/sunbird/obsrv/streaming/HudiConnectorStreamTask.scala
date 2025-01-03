package org.sunbird.obsrv.streaming

import com.typesafe.config.ConfigFactory
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.table.data.RowData
import org.apache.hudi.common.config.TimestampKeyGeneratorConfig
import org.apache.flink.metrics.{Counter, SimpleCounter}
import org.apache.hudi.configuration.{FlinkOptions, OptionsResolver}
import org.apache.hudi.sink.utils.Pipelines
import org.apache.hudi.util.AvroSchemaConverter
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.Constants
import org.sunbird.obsrv.core.streaming.{BaseStreamTask, FlinkKafkaConnector}
import org.sunbird.obsrv.core.util.FlinkUtil
import org.sunbird.obsrv.function.RowDataConverterFunction
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.util.HudiSchemaParser
import org.apache.hudi.config.HoodieWriteConfig.SCHEMA_ALLOW_AUTO_EVOLUTION_COLUMN_DROP
import org.apache.hudi.common.config.HoodieCommonConfig.SCHEMA_EVOLUTION_ENABLE
import org.apache.hudi.common.table.HoodieTableConfig.DROP_PARTITION_COLUMNS

import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable
import scala.collection.mutable.{Map => MMap}

class HudiConnectorStreamTask(config: HudiConnectorConfig, kafkaConnector: FlinkKafkaConnector) extends BaseStreamTask[mutable.Map[String, AnyRef]] {

  implicit val mutableMapTypeInfo: TypeInformation[MMap[String, AnyRef]] = TypeExtractor.getForClass(classOf[MMap[String, AnyRef]])
  private val logger = LoggerFactory.getLogger(classOf[HudiConnectorStreamTask])
  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    env.setStateBackend(new EmbeddedRocksDBStateBackend)
    process(env)
  }

  override def processStream(dataStream: DataStream[mutable.Map[String, AnyRef]]): DataStream[mutable.Map[String, AnyRef]] = {
    null
  }

  def process(env: StreamExecutionEnvironment): Unit = {
    val schemaParser = new HudiSchemaParser()
    val dataSourceConfig = DatasetRegistry.getAllDatasources().filter(f => f.`type`.nonEmpty && f.`type`.equalsIgnoreCase(Constants.DATALAKE_TYPE) && f.status.equalsIgnoreCase("Live"))



    dataSourceConfig.map { dataSource =>
      val datasetId = dataSource.datasetId
      val dataStream = getMapDataStream(env, config, List(datasetId), config.kafkaConsumerProperties(), consumerSourceName = s"kafka-${datasetId}", kafkaConnector)
        .map(new RowDataConverterFunction(config, datasetId))
        .setParallelism(config.downstreamOperatorsParallelism)

      val conf: Configuration = new Configuration()
      setHudiBaseConfigurations(conf)
      setDatasetConf(conf, datasetId, schemaParser)
      logger.info("conf: " + conf.toMap.toString)
      val rowType = schemaParser.rowTypeMap(datasetId)

      val hoodieRecordDataStream = Pipelines.bootstrap(conf, rowType, dataStream)
      val pipeline = Pipelines.hoodieStreamWrite(conf, hoodieRecordDataStream)
      if (OptionsResolver.needsAsyncCompaction(conf)) {
        Pipelines.compact(conf, pipeline).setParallelism(config.downstreamOperatorsParallelism)
      } else {
        Pipelines.clean(conf, pipeline).setParallelism(config.downstreamOperatorsParallelism)
      }
    }.orElse(List(addDefaultOperator(env, config, kafkaConnector)))

    env.execute("Flink-Hudi-Connector")
  }

  def addDefaultOperator(env: StreamExecutionEnvironment, config: HudiConnectorConfig, kafkaConnector: FlinkKafkaConnector): DataStreamSink[mutable.Map[String, AnyRef]] = {
    val dataStreamSink: DataStreamSink[mutable.Map[String, AnyRef]] = getMapDataStream(env, config, kafkaConnector)
      .sinkTo(kafkaConnector.kafkaSink[mutable.Map[String, AnyRef]](config.kafkaDefaultOutputTopic))
      .name(s"hudi-connector-default-sink").uid(s"hudi-connector-default-sink")
      .setParallelism(config.downstreamOperatorsParallelism)
    dataStreamSink
  }

  def setDatasetConf(conf: Configuration, dataset: String, schemaParser: HudiSchemaParser): Unit = {
    val datasetSchema = schemaParser.hudiSchemaMap(dataset)
    val rowType = schemaParser.rowTypeMap(dataset)
    val avroSchema = AvroSchemaConverter.convertToSchema(rowType, dataset.replace("-", "_"))
    conf.setString(FlinkOptions.PATH.key, s"${config.hudiBasePath}/${datasetSchema.schema.table}")
    conf.setString(FlinkOptions.TABLE_NAME, datasetSchema.schema.table)
    conf.setString(FlinkOptions.RECORD_KEY_FIELD.key, datasetSchema.schema.primaryKey)
    conf.setString(FlinkOptions.PRECOMBINE_FIELD.key, datasetSchema.schema.timestampColumn)
    conf.setString(FlinkOptions.PARTITION_PATH_FIELD.key, datasetSchema.schema.partitionColumn)
    conf.setString(FlinkOptions.SOURCE_AVRO_SCHEMA.key, avroSchema.toString)
    conf.setBoolean("hoodie.metrics.on", true)
    if (config.metricsReportType.equalsIgnoreCase("PROMETHEUS_PUSHGATEWAY")) {
      conf.setString("hoodie.metrics.reporter.type", config.metricsReportType)
      conf.setString("hoodie.metrics.pushgateway.host", config.metricsReporterHost)
      conf.setString("hoodie.metrics.pushgateway.port", config.metricsReporterPort)
    }
    if (config.metricsReportType.equalsIgnoreCase("JMX")) {
      conf.setString("hoodie.metrics.reporter.type", config.metricsReportType)
      conf.setString("hoodie.metrics.jmx.host", config.metricsReporterHost)
      conf.setString("hoodie.metrics.jmx.port", config.metricsReporterPort)
    }
    val partitionField = datasetSchema.schema.columnSpec.filter(f => f.name.equalsIgnoreCase(datasetSchema.schema.partitionColumn)).head
    if(partitionField.`type`.equalsIgnoreCase("timestamp") || partitionField.`type`.equalsIgnoreCase("epoch")) {
      conf.setString(FlinkOptions.PARTITION_PATH_FIELD.key, datasetSchema.schema.partitionColumn + "_partition")
    }

    if (config.hmsEnabled) {
      conf.setString("hive_sync.table", datasetSchema.schema.table)
    }
  }

  private def setHudiBaseConfigurations(conf: Configuration): Unit = {
    conf.setString(FlinkOptions.TABLE_TYPE.key, config.hudiTableType)
    conf.setBoolean(FlinkOptions.METADATA_ENABLED.key, config.hudiMetadataEnabled)

    conf.setDouble(FlinkOptions.WRITE_BATCH_SIZE.key, config.hudiWriteBatchSize)
    conf.setInteger(FlinkOptions.COMPACTION_TASKS, config.downstreamOperatorsParallelism)
    conf.setBoolean(FlinkOptions.COMPACTION_SCHEDULE_ENABLED.key, config.hudiCompactionEnabled)
    conf.setInteger(FlinkOptions.WRITE_TASKS, config.downstreamOperatorsParallelism)
    conf.setInteger(FlinkOptions.COMPACTION_DELTA_COMMITS, config.deltaCommits)
    conf.setString(FlinkOptions.COMPACTION_TRIGGER_STRATEGY, FlinkOptions.NUM_OR_TIME)
    conf.setInteger(FlinkOptions.COMPACTION_DELTA_SECONDS, config.compactionDeltaSeconds)
    conf.setBoolean(FlinkOptions.COMPACTION_ASYNC_ENABLED, true)

    conf.setString("hoodie.fs.atomic_creation.support", config.hudiFsAtomicCreationSupport)
    conf.setString(FlinkOptions.HIVE_SYNC_TABLE_PROPERTIES, "hoodie.datasource.write.drop.partition.columns=true")
    conf.setBoolean(DROP_PARTITION_COLUMNS.key, true)
    conf.setBoolean(SCHEMA_ALLOW_AUTO_EVOLUTION_COLUMN_DROP.key(), true); // Enable dropping columns
    conf.setBoolean(SCHEMA_EVOLUTION_ENABLE.key(), true); // Enable schema evolution
    conf.setString(FlinkOptions.PAYLOAD_CLASS_NAME, "org.apache.hudi.common.model.PartialUpdateAvroPayload")
    conf.setString("hoodie.parquet.compression.codec", config.compressionCodec)

    // Index Type Configurations
    conf.setString(FlinkOptions.INDEX_TYPE, config.hudiIndexType)
    conf.setInteger(FlinkOptions.BUCKET_ASSIGN_TASKS, config.downstreamOperatorsParallelism)
    conf.setDouble(FlinkOptions.WRITE_TASK_MAX_SIZE, config.hudiWriteTaskMemory)
    conf.setInteger(FlinkOptions.COMPACTION_MAX_MEMORY, config.hudiCompactionTaskMemory)

    if (config.hmsEnabled) {
      conf.setBoolean("hive_sync.enabled", config.hmsEnabled)
      conf.setString(FlinkOptions.HIVE_SYNC_DB.key(), config.hmsDatabaseName)
      conf.setString("hive_sync.username", config.hmsUsername)
      conf.setString("hive_sync.password", config.hmsPassword)
      conf.setString("hive_sync.mode", "hms")
      conf.setBoolean("hive_sync.use_jdbc", false)
      conf.setString(FlinkOptions.HIVE_SYNC_METASTORE_URIS.key(), config.hmsURI)
      conf.setString("hoodie.fs.atomic_creation.support", config.hudiFsAtomicCreationSupport)
      conf.setBoolean(FlinkOptions.HIVE_SYNC_SUPPORT_TIMESTAMP, true)
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
