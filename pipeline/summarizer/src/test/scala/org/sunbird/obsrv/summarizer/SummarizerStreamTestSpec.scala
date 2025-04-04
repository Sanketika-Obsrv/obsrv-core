package org.sunbird.obsrv.summarizer

import com.typesafe.config.{Config, ConfigFactory}
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.testutils.{InMemoryReporter, MiniClusterResourceConfiguration}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.Matchers._
import org.sunbird.obsrv.core.streaming.FlinkKafkaConnector
import org.sunbird.obsrv.core.util.FlinkUtil
import org.sunbird.obsrv.summarizer.fixture.EventFixtures
import org.sunbird.obsrv.summarizer.task.{SummarizerConfig, SummarizerStreamTask}

import scala.collection.mutable
import scala.concurrent.duration._

class SummarizerStreamTestSpec extends FlatSpec with BeforeAndAfterAll{
  // setup test environment
  val config: Config = ConfigFactory.load("test.conf")
  private val metricsReporter = InMemoryReporter.createWithRetainedMetrics
  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(metricsReporter.addToConfiguration(new Configuration()))
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)
  val pConfig = new SummarizerConfig(config)
  val kafkaConnector = new FlinkKafkaConnector(pConfig)
  val customKafkaConsumerProperties: Map[String, String] = Map[String, String]("auto.offset.reset" -> "earliest", "group.id" -> "test-event-schema-group")
  implicit val embeddedKafkaConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(
      kafkaPort = 9093,
      zooKeeperPort = 2183,
      customConsumerProperties = customKafkaConsumerProperties
    )
  implicit val deserializer: StringDeserializer = new StringDeserializer()

  override def beforeAll(): Unit = {
    EmbeddedKafka.start()(embeddedKafkaConfig)
    createTestTopics()
    flinkCluster.before()
  }

  override def afterAll(): Unit = {
    flinkCluster.after()
    EmbeddedKafka.stop()
  }

  def createTestTopics(): Unit = {
    List(
      pConfig.kafkaInputTopic, pConfig.kafkaSystemTopic, pConfig.kafkaSuccessTopic, pConfig.kafkaFailedTopic
    ).foreach(EmbeddedKafka.createCustomTopic(_))
  }

  "SummarizerStreamTestSpec" should "validate" in {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(pConfig)
    val task = new SummarizerStreamTask(pConfig, kafkaConnector)
    task.process(env)
    env.executeAsync(pConfig.jobName)
    validateCorrectFlow()
    validateNoEnd()
    validateIdleTime()
    validateNoStart()
    validateMultipleStart()
    validateValidOutOfOrder()
    validateInvalidOutOfOrder()
  }

  def getTotalTimeSpent(event: mutable.Map[String, AnyRef], timeSpent: Long): Unit = {
    val edata = event.getOrElse("edata", mutable.Map[String, AnyRef]()).asInstanceOf[mutable.Map[String, AnyRef]]
    val eks = edata.getOrElse("eks", mutable.Map[String, AnyRef]()).asInstanceOf[mutable.Map[String, AnyRef]]
    val totalTimeSpent = eks.getOrElse("totalTimeSpent", 0)
    totalTimeSpent should be(timeSpent)
  }

  // START, INTERACT, LOG, END
  def validateCorrectFlow(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.AUDIT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.END)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 1, timeout = 130.seconds)
    outputEvents.size should be(1)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 64000)
  }

  // START, INTERACT. Should time out after sessionBreakTime
  def validateNoEnd(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.INTERACT)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 1, timeout = 130.seconds)
    outputEvents.size should be(1)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 5000)
  }

  // START, IMPRESSION, END. should skip idleTime from total time
  def validateIdleTime(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.IMPRESSION)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.END)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 1, timeout = 130.seconds)
    outputEvents.size should be(1)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 3000)
  }

  // INTERACT, END. Should assume start at INTERACT ets
  def validateNoStart(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.END)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 1, timeout = 130.seconds)
    outputEvents.size should be(1)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 59000)
  }

  // START, INTERACT, SECOND_START, END. Should close first session and start new session
  def validateMultipleStart(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.SECOND_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.END)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 2, timeout = 130.seconds)
    outputEvents.size should be(2)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 62000)
    getTotalTimeSpent(outputEvents(1).asInstanceOf[mutable.Map[String, AnyRef]], 2000)
  }

  // INTERACT, START, END. watermark strategy should wait for START
  def validateValidOutOfOrder(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.END)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 1, timeout = 130.seconds)
    outputEvents.size should be(1)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 64000)
  }

  // IMPRESSION, START, END. watermark strategy should send IMPRESSION as START reaches after time bound
  def validateInvalidOutOfOrder(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.IMPRESSION)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.END)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 2, timeout = 130.seconds)
    outputEvents.size should be(2)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 0)
    getTotalTimeSpent(outputEvents.head.asInstanceOf[mutable.Map[String, AnyRef]], 0)
  }

}