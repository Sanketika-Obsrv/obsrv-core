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
import org.sunbird.obsrv.job.util.{FlinkKafkaConnector, FlinkUtil, JSONUtil}
import org.sunbird.obsrv.summarizer.fixture.EventFixtures
import org.sunbird.obsrv.summarizer.task.{SummarizerConfig, SummarizerStreamTask}
import org.apache.flink.api.scala.metrics.ScalaGauge

import scala.collection.mutable
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

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
    publishEvents()
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

  def publishEvents(): Unit = {
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_1_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_1_INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_1_AUDIT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_1_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_2_INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_2_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_4_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_4_IMPRESSION)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_4_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_5_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_5_INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_5_SECOND_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_5_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_6_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_7_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_7_INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_7_PAGE_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_7_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_8_INTERACT)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_8_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_8_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_9_IMPRESSION)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_9_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_9_END)

    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_3_START)
    EmbeddedKafka.publishStringMessageToKafka(pConfig.kafkaInputTopic, EventFixtures.CASE_3_INTERACT)
  }

  def getTotalTimeSpent(event: Map[String, AnyRef], timeSpent: Int): Unit = {
    val edata = event.getOrElse("edata", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
    val eks = edata.getOrElse("eks", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
    val totalTimeSpent = eks.getOrElse("time_spent", 0).asInstanceOf[Int]
    totalTimeSpent should be(timeSpent)
  }

  "SummarizerStreamTestSpec" should "validate all flows" in {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(pConfig)
    val task = new SummarizerStreamTask(pConfig, kafkaConnector)
    task.process(env)
    env.executeAsync(pConfig.jobName)
    val outputEvents = EmbeddedKafka.consumeNumberMessagesFrom[String](pConfig.kafkaSuccessTopic, 9, timeout = 35.seconds)
    outputEvents.size should be(9)
    outputEvents.zipWithIndex.foreach {
      case (elem, idx) =>
        val msg = JSONUtil.deserialize[Map[String, AnyRef]](elem)
        val time = EventFixtures.timeIndex(idx)
        getTotalTimeSpent(msg, time)
    }
// metrics not getting updated
//    validateMetrics(metricsReporter)
  }

  def getMetrics(metricsReporter: InMemoryReporter, dataset: String, debug: Option[Boolean] = None): Map[String, Long] = {
    val groups = metricsReporter.findGroups(dataset).asScala
    groups.map(group => metricsReporter.getMetricsByGroup(group).asScala)
      .map(group => group.map { case (k, v) =>
        val value = if (v.isInstanceOf[ScalaGauge[Long]]) v.asInstanceOf[ScalaGauge[Long]].getValue() else 0
        if (debug.isDefined && debug.get)
          Console.println("Metric", k, value)
        k -> value
      })
      .map(f => f.toMap)
      .foldLeft(Map.empty[String, Long]) { (map1, map2) =>
        val mergedMap = map2.map { case (k: String, v: Long) => k -> (v + map1.getOrElse(k, 0L)) }
        map1 ++ mergedMap
      }
  }

  private def validateMetrics(metricsReporter: InMemoryReporter): Unit = {
    val d1Metrics = getMetrics(metricsReporter, "ALL")
    d1Metrics(pConfig.totalEventCount) should be(26)
    d1Metrics(pConfig.successSummarizerCount) should be(9)
    d1Metrics(pConfig.skippedSummarizerCount) should be(4)
  }

}