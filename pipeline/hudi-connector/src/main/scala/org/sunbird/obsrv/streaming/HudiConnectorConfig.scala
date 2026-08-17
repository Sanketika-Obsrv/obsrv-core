package org.sunbird.obsrv.streaming

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.apache.hudi.common.model.HoodieTableType
import org.sunbird.obsrv.core.streaming.BaseJobConfig

import scala.collection.mutable

class HudiConnectorConfig(override val config: Config) extends BaseJobConfig[mutable.Map[String, AnyRef]](config, "Flink-Hudi-Connector") {

  implicit val mapTypeInfo: TypeInformation[mutable.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[mutable.Map[String, AnyRef]])

  override def inputTopic(): String = config.getString("kafka.input.topic")

  val kafkaDefaultOutputTopic: String = config.getString("kafka.output.topic")

  override def inputConsumer(): String = config.getString("kafka.groupId")

  override def successTag(): OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]]("dummy-events")

  override def failedEventsOutputTag(): OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]]("failed-events")

  val kafkaInvalidTopic: String = config.getString("kafka.output.invalid.topic")

  val invalidEventsOutputTag: OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]]("invalid-events")
  val validEventsOutputTag: OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]]("valid-events")

  val invalidEventProducer = "invalid-events-sink"


  val hudiTableType: String =
    if (config.getString("hudi.table.type").equalsIgnoreCase("MERGE_ON_READ"))
      HoodieTableType.MERGE_ON_READ.name()
    else if (config.getString("hudi.table.type").equalsIgnoreCase("COPY_ON_WRITE"))
      HoodieTableType.COPY_ON_WRITE.name()
    else HoodieTableType.MERGE_ON_READ.name()

  val hudiBasePath: String = config.getString("hudi.table.base.path")

  val hmsEnabled: Boolean = if (config.hasPath("hudi.hms.enabled")) config.getBoolean("hudi.hms.enabled") else false
  // Was unconditional config.getString(...) regardless of hmsEnabled - a deployment with HMS
  // disabled and these 4 keys reasonably omitted (no Hive Metastore to configure) crashed on
  // startup with ConfigException.Missing anyway. Only required when actually enabled.
  val hmsUsername: String = if (hmsEnabled) config.getString("hudi.hms.database.username") else ""
  val hmsPassword: String = if (hmsEnabled) config.getString("hudi.hms.database.password") else ""
  val hmsDatabaseName: String = if (hmsEnabled) config.getString("hudi.hms.database.name") else ""
  val hmsURI: String = if (hmsEnabled) config.getString("hudi.hms.uri") else ""

  val hudiWriteTasks: Int = config.getInt("hudi.write.tasks")
  val hudiCompactionTasks: Int = config.getInt("hudi.compaction.tasks")
  val hudiWriteBatchSize: Int = config.getInt("hudi.write.batch.size")
  val deltaCommits: Int = config.getInt("hudi.delta.commits")
  val compactionDeltaSeconds: Int = config.getInt("hudi.delta.seconds")
  val compressionCodec: String = config.getString("hudi.compression.codec")
  val hudiCompactionEnabled: Boolean = config.getBoolean("hudi.compaction.enabled")
  val hudiMetadataEnabled: Boolean = config.getBoolean("hudi.metadata.enabled")
  val hudiIndexType: String = config.getString("hudi.index.type")

  // Memory
  val hudiWriteTaskMemory: Int = config.getInt("hudi.write.task.max.memory")
  val hudiCompactionTaskMemory: Int = config.getInt("hudi.write.compaction.max.memory")
  // None of these 4 keys exist in hudi-writer.conf - mandatory config.getString(...) calls
  // crashed HudiConnectorConfig's constructor immediately on startup with
  // ConfigException.Missing, every single time, with the default shipped config.
  // hoodie.fs.atomic_creation.support takes a comma-separated list of filesystem schemes
  // (e.g. "hdfs,file,viewfs") that support atomic creation, for Hudi's FileSystemBasedLockProvider
  // - not a boolean. "true" would never match a real scheme; empty default leaves Hudi's own
  // built-in scheme list untouched.
  val hudiFsAtomicCreationSupport: String = if (config.hasPath("hudi.fs.atomic_creation.support")) config.getString("hudi.fs.atomic_creation.support") else ""

  // Metrics

  val inputEventCountMetric = "input-event-count"
  val failedEventCountMetric = "failed-event-count"

  // Metrics Exporter
  val metricsReportType: String = if (config.hasPath("metrics.reporter.type")) config.getString("metrics.reporter.type") else "NONE"
  val metricsReporterHost: String = if (config.hasPath("metrics.reporter.host")) config.getString("metrics.reporter.host") else "localhost"
  val metricsReporterPort: String = if (config.hasPath("metrics.reporter.port")) config.getString("metrics.reporter.port") else "9091"


}
