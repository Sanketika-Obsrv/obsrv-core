package org.sunbird.obsrv.summarizer.task

import org.sunbird.obsrv.job.util.BaseJobConfig

import scala.collection.mutable
import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag

case class SummaryKey(did: String, channel: String, pdata_id: String)

class SummaryKeySelector extends KeySelector[mutable.Map[String, AnyRef], SummaryKey] {
  override def getKey(event: mutable.Map[String, AnyRef]): SummaryKey = {
    val cdata = event.get("context") match {
      case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
      case _ => mutable.Map[String, AnyRef]()
    }
    val pdata = cdata.get("pdata") match {
      case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
      case _ => mutable.Map[String, AnyRef]()
    }
    val did = cdata.getOrElse("did", "").toString
    val channel = cdata.getOrElse("channel", "").toString
    val pdataId = pdata.getOrElse("id", "").toString
    SummaryKey(did, channel, pdataId)
  }
}

class SummarizerConfig(override val config: Config) extends BaseJobConfig[mutable.Map[String, AnyRef]](config, "SummarizerJob") {

  private val serialVersionUID = 2905979434303791379L

  implicit val mapTypeInfo: TypeInformation[mutable.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[mutable.Map[String, AnyRef]])
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

  // Metric List
  val totalEventCount = "summarizer-total-count"
  val successEventCount = "summarizer-event-count"
  val failedSummarizerCount = "summarizer-failed-count"
  val successSummarizerCount = "summarizer-success-count"
  val skippedSummarizerCount = "summarizer-skipped-count"

  val idleTime: Long = config.getLong("idleTime")
  val sessionBreakTime: Long = config.getLong("sessionBreakTime")
  val waterMarkTimeBound: Long = config.getLong("waterMarkTimeBound")
  
  // Kafka Topics Configuration
  val kafkaInputTopic: String = config.getString("kafka.input.topic")
  val kafkaSuccessTopic: String = config.getString("kafka.output.summary.topic")

  // Functions
  val summarizerFunction = "SummarizerFunction"

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
