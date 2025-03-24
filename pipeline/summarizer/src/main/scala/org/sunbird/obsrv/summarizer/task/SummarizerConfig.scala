package org.sunbird.obsrv.summarizer.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeSummarizer
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.obsrv.core.model.SystemConfig
import org.sunbird.obsrv.core.streaming.BaseJobConfig

import scala.collection.mutable

class SummarizerConfig(override val config: Config) extends BaseJobConfig[mutable.Map[String, AnyRef]](config, "SummarizerJob") {

  private val serialVersionUID = 2905979434303791379L

  implicit val mapTypeInfo: TypeInformation[mutable.Map[String, AnyRef]] = TypeSummarizer.getForClass(classOf[mutable.Map[String, AnyRef]])
  implicit val stringTypeInfo: TypeInformation[String] = TypeSummarizer.getForClass(classOf[String])

  // Metric List
  val totalEventCount = "summarizer-total-count"
  val successEventCount = "summarizer-event-count"
  val failedSummarizationCount = "summarizer-failed-count"
  val successSummarizationCount = "summarizer-success-count"
  
  // Kafka Topics Configuration
  val kafkaInputTopic: String = config.getString("kafka.input.topic")
  val kafkaSuccessTopic: String = config.getString("kafka.output.summary.topic")
  val kafkaFailedTopic: String = config.getString("kafka.output.summary.failed.topic")

  // Functions
  val summarizationFunction = "SummarizationFunction"

  // Producers
  val summarizerFailedEventsProducer = "summarizer-failed-producer"
  val summarizerSuccessEventsProducer = "summarizer-producer"

  // OUTPUT TAGS
  private val SUMMARIZER_EVENTS = "summarizer-events"
  private val SUMMARIZER_FAILED_EVENTS = "summarizer-failed-events"

  val summarizerEventsOutputTag: OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]](SUMMARIZER_EVENTS)
  val summarizerFailedEventOutputTag: OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]](SUMMARIZER_FAILED_EVENTS)


  override def inputTopic(): String = kafkaInputTopic
  override def inputConsumer(): String = "summarizer-consumer"
  override def successTag(): OutputTag[mutable.Map[String, AnyRef]] = summarizerEventsOutputTag
  override def failedEventsOutputTag(): OutputTag[mutable.Map[String, AnyRef]] = OutputTag[mutable.Map[String, AnyRef]]("failed-events")
}
