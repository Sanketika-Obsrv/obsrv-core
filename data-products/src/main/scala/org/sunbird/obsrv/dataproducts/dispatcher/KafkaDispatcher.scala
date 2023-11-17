package org.sunbird.obsrv.dataproducts.dispatcher

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.sql.{DataFrame, Dataset}
import org.sunbird.obsrv.dataproducts.exception.InvalidInputException
import org.sunbird.obsrv.dataproducts.model.{Dispatcher, JobConfig}


case class KafkaDispatcher(jobConfig: JobConfig, dispatcherConfig: Map[String, AnyRef]) extends Dispatcher {
  override val config: Map[String, AnyRef] = Map() ++ dispatcherConfig
  override val dispatcherType: String = "kafka"

  private val producer = createKafkaProducer()

  private def getProducer: KafkaProducer[Long, String] = producer

  def sendEvent(topic: String, event: String): Unit = {
    val producer = getProducer
    try {
      producer.send(new ProducerRecord[Long, String](topic, event))
    } catch {
      case e: Exception => throw new Exception(s"Error while sending event to kafka: $e")
    }
  }

  private def createKafkaProducer(): KafkaProducer[Long, String] = {
    val props = new java.util.Properties()
    props.put("bootstrap.servers", config.getOrElse("kafka.bootstrap.servers", throw InvalidInputException("Kafka bootstrap server is not provided")).asInstanceOf[String])
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[Long, String](props)
  }

  override def dispatch[T](records: Dataset[T], config: Map[String, AnyRef]): Unit = {

    val topic = config.getOrElse("topic", throw InvalidInputException("Kafka topic is not provided")).asInstanceOf[String]
    val bootstrapServers = config.getOrElse("kafka.bootstrap.servers", throw InvalidInputException("Kafka bootstrap server is not provided")).asInstanceOf[String]

    try {
      records.selectExpr("CAST(id as string)", "to_json(struct(*)) AS value")
        .write
        .format(dispatcherType)
        .option("kafka.bootstrap.servers", bootstrapServers)
        .option("topic", topic)
        .save()
    } catch {
      case e: Exception => throw new Exception(s"Error while dispatching data to kafka: $e")
    }
  }

  override def dispatchData(records: DataFrame, config: Map[String, AnyRef]): Unit = {
    val topic = config.getOrElse("topic", throw InvalidInputException("Kafka topic is not provided")).asInstanceOf[String]
    val bootstrapServers = config.getOrElse("kafka.bootstrap.servers", throw InvalidInputException("Kafka bootstrap server is not provided")).asInstanceOf[String]

    try {
      records.selectExpr("CAST(id as string)", "to_json(struct(*)) AS value")
        .write
        .format(dispatcherType)
        .option("kafka.bootstrap.servers", bootstrapServers)
        .option("topic", topic)
        .save()
    } catch {
      case e: Exception => throw new Exception(s"Error while dispatching data to kafka: $e")
    }
  }

}
