package org.sunbird.obsrv.pipeline

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.Matchers._
import org.sunbird.obsrv.BaseMetricsReporter
import org.sunbird.obsrv.core.cache.RedisConnect
import org.sunbird.obsrv.core.streaming.FlinkKafkaConnector
import org.sunbird.obsrv.core.util.{FlinkUtil, JSONUtil}
import org.sunbird.obsrv.fixture.EventFixture
import org.sunbird.obsrv.pipeline.task.{MergedPipelineConfig, MergedPipelineStreamTask}
import org.sunbird.obsrv.spec.BaseSpecWithDatasetRegistry

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class MergedPipelineStreamTaskTestSpec extends BaseSpecWithDatasetRegistry {

  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(testConfiguration())
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)

  val mergedPipelineConfig = new MergedPipelineConfig(config)
  val kafkaConnector = new FlinkKafkaConnector(mergedPipelineConfig)
  val customKafkaConsumerProperties: Map[String, String] = Map[String, String]("auto.offset.reset" -> "earliest", "group.id" -> "test-event-schema-group")
  implicit val embeddedKafkaConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(
      kafkaPort = 9093,
      zooKeeperPort = 2183,
      customConsumerProperties = customKafkaConsumerProperties
    )
  implicit val deserializer: StringDeserializer = new StringDeserializer()

  def testConfiguration(): Configuration = {
    val config = new Configuration()
    config.setString("metrics.reporter", "job_metrics_reporter")
    config.setString("metrics.reporter.job_metrics_reporter.class", classOf[BaseMetricsReporter].getName)
    config
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    BaseMetricsReporter.gaugeMetrics.clear()
    EmbeddedKafka.start()(embeddedKafkaConfig)
    createTestTopics()
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.VALID_BATCH_EVENT_D1)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.MISSING_DATASET_BATCH_EVENT)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.UNREGISTERED_DATASET_BATCH_EVENT)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.DUPLICATE_BATCH_EVENT)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.INVALID_BATCH_EVENT_INCORRECT_EXTRACTION_KEY)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.INVALID_BATCH_EVENT_EXTRACTION_KEY_NOT_ARRAY)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.VALID_BATCH_EVENT_D2)
    EmbeddedKafka.publishStringMessageToKafka(config.getString("kafka.input.topic"), EventFixture.INVALID_BATCH_EVENT_D2)
    flinkCluster.before()
  }

  override def afterAll(): Unit = {
    val redisConnection = new RedisConnect(mergedPipelineConfig.redisHost, mergedPipelineConfig.redisPort, mergedPipelineConfig.redisConnectionTimeout)
    redisConnection.getConnection(config.getInt("redis.database.extractor.duplication.store.id")).flushAll()
    redisConnection.getConnection(config.getInt("redis.database.preprocessor.duplication.store.id")).flushAll()
    super.afterAll()
    flinkCluster.after()
    EmbeddedKafka.stop()
  }

  def createTestTopics(): Unit = {
    List(
      config.getString("kafka.output.system.event.topic"), config.getString("kafka.output.transform.topic"), config.getString("kafka.output.denorm.failed.topic"),
      config.getString("kafka.output.denorm.topic"), config.getString("kafka.output.duplicate.topic"), config.getString("kafka.output.unique.topic"),
      config.getString("kafka.output.invalid.topic"), config.getString("kafka.output.batch.failed.topic"), config.getString("kafka.output.failed.topic"),
      config.getString("kafka.output.extractor.duplicate.topic"), config.getString("kafka.output.raw.topic"), config.getString("kafka.input.topic"),
      "d1-events", "d2-events"
    ).foreach(EmbeddedKafka.createCustomTopic(_))
  }

  "MergedPipelineStreamTaskTestSpec" should "validate the entire pipeline" in {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(mergedPipelineConfig)
    val task = new MergedPipelineStreamTask(config, mergedPipelineConfig, kafkaConnector)
    task.process(env)
    Future {
      env.execute(mergedPipelineConfig.jobName)
    }

//    val input = EmbeddedKafka.consumeNumberMessagesFrom[String](config.getString("kafka.output.raw.topic"), 1, timeout = 30.seconds)
//    input.foreach(Console.println("Input Event:", _))

    try {
      val systemEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](config.getString("kafka.output.system.event.topic"), 1, timeout = 30.seconds)
      systemEvents.foreach(Console.println("System Event:", _))
    } catch {
      case ex: Exception => ex.printStackTrace()
    }
    try {
      val failed = EmbeddedKafka.consumeNumberMessagesFrom[String](config.getString("kafka.output.failed.topic"), 1, timeout = 30.seconds)
      failed.foreach(Console.println("Failed Event:", _))
    } catch {
      case ex: Exception => ex.printStackTrace()
    }


    val mutableMetricsMap = mutable.Map[String, Long]();
    BaseMetricsReporter.gaugeMetrics.toMap.mapValues(f => f.getValue()).map(f => mutableMetricsMap.put(f._1, f._2))
    Console.println("### MergedPipelineStreamTaskTestSpec:metrics ###", JSONUtil.serialize(getPrintableMetrics(mutableMetricsMap)))

    //TODO: Add assertions
    mergedPipelineConfig.successTag().getId should be ("processing_stats")
    
  }


}
