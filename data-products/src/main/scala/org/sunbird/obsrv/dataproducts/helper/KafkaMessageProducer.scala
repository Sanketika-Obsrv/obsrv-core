package org.sunbird.obsrv.helpers

import com.typesafe.config.Config
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.slf4j.LoggerFactory

import java.util.Properties

case class KafkaMessageProducer(config: Config) {

  private val kafkaProperties = new Properties();
  private val defaultTopicName = config.getString("metrics.topicName")
  private val defaultKey = null
  private[this] val logger = LoggerFactory.getLogger(KafkaMessageProducer.getClass)

  kafkaProperties.put("bootstrap.servers", config.getString("kafka.bootstrap.servers"))
  kafkaProperties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  kafkaProperties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  val producer = new KafkaProducer[String, String](kafkaProperties)

  def sendMessage(topic: String = defaultTopicName, key: String = defaultKey, message: String): Unit = {
    try {
      val record = new ProducerRecord[String, String](topic, key, message)
      producer.send(record)
    } catch {
      case e: Exception =>
        logger.error("Exception while sending message to Kafka")
        logger.error("Exception - "+ e.getMessage)
    }
  }
}
