// package org.sunbird.obsrv.streaming

// import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
// import org.apache.flink.configuration.Configuration
// import org.apache.flink.runtime.testutils.{InMemoryReporter, MiniClusterResourceConfiguration}
// import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
// import org.apache.flink.test.util.MiniClusterWithClientResource
// import org.apache.kafka.common.serialization.StringDeserializer
// import org.scalatest.Matchers._
// import org.sunbird.obsrv.core.streaming.FlinkKafkaConnector
// import org.sunbird.obsrv.core.util.FlinkUtil
// import org.sunbird.obsrv.spec.BaseSpecWithDatasetRegistry
// import org.sunbird.obsrv.streaming.{HudiConnectorConfig, HudiConnectorStreamTask}

// import scala.concurrent.duration._

// class HudiConnectorKafkaFlinkIT extends BaseSpecWithDatasetRegistry {

//   private val metricsReporter = InMemoryReporter.createWithRetainedMetrics
//   val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
//     .setConfiguration(metricsReporter.addToConfiguration(new Configuration()))
//     .setNumberSlotsPerTaskManager(1)
//     .setNumberTaskManagers(1)
//     .build)

//   val hudiTestConfig = config // Uses test.conf via BaseSpecWithDatasetRegistry
//   val hudiConnectorConfig = new HudiConnectorConfig(hudiTestConfig)
//   val kafkaConnector = new FlinkKafkaConnector(hudiConnectorConfig)
//   val customKafkaConsumerProperties: Map[String, String] = Map[String, String]("auto.offset.reset" -> "earliest", "group.id" -> "hudi-connector-test-group")
//   implicit val embeddedKafkaConfig: EmbeddedKafkaConfig =
//     EmbeddedKafkaConfig(
//       kafkaPort = 9093,
//       zooKeeperPort = 2183,
//       customConsumerProperties = customKafkaConsumerProperties
//     )
//   implicit val deserializer: StringDeserializer = new StringDeserializer()

//   override def beforeAll(): Unit = {
//     super.beforeAll()
//     EmbeddedKafka.start()(embeddedKafkaConfig)
//     createTestTopics()
//     flinkCluster.before()
//   }

//   override def afterAll(): Unit = {
//     super.afterAll()
//     flinkCluster.after()
//     EmbeddedKafka.stop()
//   }

//   def createTestTopics(): Unit = {
//     List(hudiConnectorConfig.inputTopic(), hudiConnectorConfig.kafkaDefaultOutputTopic, hudiConnectorConfig.kafkaInvalidTopic).foreach(EmbeddedKafka.createCustomTopic(_))
//   }

//   test("write to Hudi from Kafka via Flink") {
//     // Publish a test event to Kafka input topic
//     val testEvent = """{""id"":""test1"",""value"":123,""ts"":""2024-07-01T12:00:00.000Z""}"""
//     EmbeddedKafka.publishStringMessageToKafka(hudiConnectorConfig.inputTopic(), testEvent)

//     // Run the Hudi connector job
//     implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(hudiConnectorConfig)
//     val task = new HudiConnectorStreamTask(hudiConnectorConfig, kafkaConnector)
//     task.process(env)
//     env.executeAsync(hudiConnectorConfig.jobName)

//     // TODO: Validate data written to Hudi (e.g., by reading from the Hudi table or checking output topic)
//     // For now, just check that the job runs and Kafka output topic is available
//     val output = EmbeddedKafka.consumeNumberMessagesFrom[String](hudiConnectorConfig.kafkaDefaultOutputTopic, 1, timeout = 30.seconds)
//     output.size should be >= 0 // Placeholder assertion
//   }

//   test("read from Hudi to Kafka via Flink") {
//     // TODO: Implement a test that reads from Hudi and writes to Kafka (if supported by the job)
//     // For now, this is a placeholder for future expansion
//     succeed
//   }
// }