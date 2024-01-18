package org.sunbird.obsrv.writer

import com.typesafe.config.ConfigFactory
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.data.{GenericRowData, RowData, StringData}
import org.apache.hudi.common.model.HoodieTableType
import org.apache.hudi.configuration.FlinkOptions
import org.apache.hudi.util.HoodiePipeline
import org.sunbird.obsrv.core.streaming.{BaseStreamTask, FlinkKafkaConnector}
import org.sunbird.obsrv.core.util.FlinkUtil

import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util
import scala.collection.mutable

class HudiWriterStreamTask (config: HudiWriterConfig, kafkaConnector: FlinkKafkaConnector) extends BaseStreamTask[String] {

  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    process(env)
    env.execute(config.jobName)
  }

  override def processStream(dataStream: DataStream[String]): DataStream[String] = {
    null
  }

  def process(env: StreamExecutionEnvironment): Unit = {
    val targetTable = "financial_txns";
//    val basePath = s"file:///tmp/$targetTable"
//    val basePath = s"s3a://obsrv/data/$targetTable"
    val basePath = s"file:///tmp/hudi/obsrv/data/$targetTable"
//    val options = setHMSConfig(getOptions(basePath))
    val options = getOptions(basePath)
    val dataStream = getMapDataStream(env, config, kafkaConnector)

    val rowDataStream = dataStream.map(new MapFunction[mutable.Map[String, AnyRef], RowData] {
      override def map(t: mutable.Map[String, AnyRef]): RowData = {
        val rowData = new GenericRowData(11)
        rowData.setField(0, StringData.fromString(t.getOrElse("txn_id", "").toString))
        rowData.setField(1, StringData.fromString(t.getOrElse("sender_ifsc_code", "").toString))
        rowData.setField(2, StringData.fromString(t.getOrElse("sender_account_number", "").toString))
        rowData.setField(3, StringData.fromString(t.getOrElse("receiver_ifsc_code", "").toString))
        rowData.setField(4, StringData.fromString(t.getOrElse("receiver_account_number", "").toString))
        rowData.setField(5, StringData.fromString(t.getOrElse("sender_contact_email", "").toString))
        rowData.setField(6, StringData.fromString(t.getOrElse("txn_type", "").toString))
        rowData.setField(7, t.getOrElse("txn_amount", "").asInstanceOf[Integer])
        rowData.setField(8, StringData.fromString(t.getOrElse("txn_status", "").toString))
        rowData.setField(9, StringData.fromString(t.getOrElse("currency", "").toString))
        rowData.setField(10, org.apache.flink.table.data.TimestampData.fromTimestamp(HudiWriterStreamTask.getTimestamp(t.getOrElse("txn_date","").toString)))
        rowData
      }
    })

    val builder = HoodiePipeline.builder(targetTable)
      .column("txn_id VARCHAR(20)")
      .column("sender_ifsc_code VARCHAR(20)")
      .column("sender_account_number VARCHAR(20)")
      .column("receiver_ifsc_code VARCHAR(20)")
      .column("receiver_account_number VARCHAR(20)")
      .column("sender_contact_email VARCHAR(40)")
      .column("txn_type VARCHAR(10)")
      .column("txn_amount INT")
      .column("txn_status VARCHAR(10)")
      .column("currency VARCHAR(10)")
      .column("txn_date timestamp(3)")
      .pk("txn_id")
      .options(options)

    builder.sink(rowDataStream, false)
  }

  private def getOptions(basePath: String): util.Map[String, String] = {
    val options = new util.HashMap[String, String]()
    options.put(FlinkOptions.PATH.key, basePath)
    options.put(FlinkOptions.TABLE_TYPE.key, HoodieTableType.MERGE_ON_READ.name())
    options.put(FlinkOptions.METADATA_ENABLED.key(), "true")
    options.put(FlinkOptions.PRECOMBINE_FIELD.key, "txn_date")
    options.put(FlinkOptions.WRITE_BATCH_SIZE.key(), "0.1")
    options.put(FlinkOptions.PARTITION_PATH_FIELD.key(), "sender_ifsc_code")
    options.put(FlinkOptions.COMPACTION_DELTA_COMMITS.key(), "0")

//    options.put("hoodie.compaction.strategy", "org.apache.hudi.table.action.compact.strategy.LogFileNumBasedCompactionStrategy")
//    options.put("hoodie.compaction.logfile.num.threshold", "1")
//    options.put("hoodie.parquet.block.size", "1024")
//    options.put("hoodie.parquet.max.file.size", "1024")

    // To handle bucket_assigner
//    options.put(FlinkOptions.WRITE_TASK_MAX_SIZE.key(), "250")
//    options.put(FlinkOptions.BUCKET_ASSIGN_TASKS.key(), "5")

    options.put("hoodie.compaction.strategy", "org.apache.hudi.table.action.compact.strategy.UnBoundedCompactionStrategy")
    options.put("hoodie.parquet.block.size", "500")
    options.put("hoodie.parquet.max.file.size", "500")

    options.put("write.tasks", "1")
    options.put("compaction.tasks", "1")
    options.put("compaction.async.enabled", "true")


//    options.put(FlinkOptions.KEYGEN_CLASS_NAME.key(), "org.apache.hudi.keygen.TimestampBasedKeyGenerator")
//    options.put("hoodie.deltastreamer.keygen.timebased.timestamp.type", "DATE_STRING")
//    options.put("hoodie.keygen.timebased.input.dateformat", "yyyy-MM-ddTHH:mm:ss.SSSZ")
//    options.put("hoodie.deltastreamer.keygen.timebased.output.dateformat", "yyyy-MM-dd")
//    options.put("hoodie.datasource.write.record.writer", "org.apache.hudi.writer.DFSParquetConfig")
//    options.put("hoodie.parquet.write.useApiV1", "false")
    options
  }

  private def setLogFileNumBasedCompaction(options: util.Map[String, String]) = {
    options.put("hoodie.compaction.strategy", "org.apache.hudi.table.action.compact.strategy.LogFileNumBasedCompactionStrategy")
    options.put("hoodie.compaction.logfile.num.threshold", "1")
    options.put("hoodie.parquet.block.size", "1024")
    options.put("hoodie.parquet.max.file.size", "1024")
    options
  }

  private def setUnBoundedCompaction(options: util.Map[String, String]) = {
    options.put("hoodie.compaction.strategy", "org.apache.hudi.table.action.compact.strategy.UnBoundedCompactionStrategy")
    options.put("hoodie.parquet.block.size", "500")
    options.put("hoodie.parquet.max.file.size", "500")
    options
  }

  private def setHMSConfig(options: util.Map[String, String]) = {
    options.put("hive_sync.enabled", "true")
    options.put(FlinkOptions.HIVE_SYNC_DB.key(), "obsrv")
    options.put("hive_sync.table", "financial_txns")
    options.put("hive_sync.username", "admin")
    options.put("hive_sync.password", "secret")
    options.put("hive_sync.mode", "hms")
    options.put("hive_sync.use_jdbc", "false")
    options.put(FlinkOptions.HIVE_SYNC_METASTORE_URIS.key(), "thrift://localhost:9083")
    options
  }

}

object HudiWriterStreamTask {
  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("hudi-writer.conf").withFallback(ConfigFactory.systemEnvironment()))
    val hudiWriterConfig = new HudiWriterConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(hudiWriterConfig)
    val task = new HudiWriterStreamTask(hudiWriterConfig, kafkaUtil)
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
