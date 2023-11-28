package org.sunbird.obsrv.router.functions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.{Constants, ErrorConstants, FunctionalError, Producer}
import org.sunbird.obsrv.core.streaming.Metrics
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.router.task.DruidRouterConfig
import org.sunbird.obsrv.streaming.BaseDatasetProcessFunction

import java.util.TimeZone
import scala.collection.mutable

case class TimestampKey(isValid: Boolean, value: AnyRef)

class DynamicRouterFunction(config: DruidRouterConfig) extends BaseDatasetProcessFunction(config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[DynamicRouterFunction])

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
  }

  override def close(): Unit = {
    super.close()
  }

  override def getMetrics(): List[String] = {
    List(config.routerTotalCount, config.routerSuccessCount)
  }

  override def processElement(dataset: Dataset, msg: mutable.Map[String, AnyRef],
                              ctx: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {

    metrics.incCounter(dataset.id, config.routerTotalCount)
    val event = Util.getMutableMap(msg(config.CONST_EVENT).asInstanceOf[Map[String, AnyRef]])
    val tsKeyData = parseTimestampKey(dataset, event)
    if (tsKeyData.isValid) {
      event.put(config.CONST_OBSRV_META, msg(config.CONST_OBSRV_META).asInstanceOf[Map[String, AnyRef]] ++ Map("indexTS" -> tsKeyData.value))
      val routerConfig = dataset.routerConfig
      val topicEventMap = mutable.Map(Constants.TOPIC -> routerConfig.topic, Constants.MESSAGE -> event)
      ctx.output(config.routerOutputTag, topicEventMap)
      metrics.incCounter(dataset.id, config.routerSuccessCount)
      msg.remove(config.CONST_EVENT)
      markCompletion(dataset, super.markComplete(event, dataset.dataVersion), ctx)
    } else {
      markFailure(Some(dataset.id), event, ctx, metrics, ErrorConstants.INDEX_KEY_MISSING_OR_BLANK, Producer.router, FunctionalError.MissingTimestampKey)
    }
  }

  private def parseTimestampKey(dataset: Dataset, event: mutable.Map[String, AnyRef]): TimestampKey = {
    val indexKey = dataset.datasetConfig.tsKey
    val node = JSONUtil.getKey(indexKey, JSONUtil.serialize(event))
    node.getNodeType match {
      case JsonNodeType.NUMBER => onNumber(dataset, node)
      case JsonNodeType.STRING => onText(dataset, node)
      case _ => TimestampKey(isValid = false, null)
    }
  }

  private def onNumber(dataset: Dataset, node: JsonNode): TimestampKey = {
    val length = node.textValue().length
    val value = node.numberValue().longValue()
    // TODO: [P3] Crude implementation. Checking if the epoch timestamp format is one of seconds, milli-seconds, micro-second and nano-seconds. Find a elegant approach
    if (length == 10 || length == 13 || length == 16 || length == 19) {
      val tfValue = if (length == 10) value * 1000 else if (length == 16) value / 1000 else if (length == 19) value / 1000000
      TimestampKey(isValid = true, addTimeZone(dataset, new DateTime(tfValue)).asInstanceOf[AnyRef])
    } else {
      TimestampKey(isValid = false, 0.asInstanceOf[AnyRef])
    }
  }

  private def onText(dataset: Dataset, node: JsonNode): TimestampKey = {
    val value = node.textValue()
    if (dataset.datasetConfig.tsFormat.isDefined) {
      parseDateTime(dataset, value)
    } else {
      TimestampKey(isValid = true, value)
    }
  }

  private def parseDateTime(dataset: Dataset, value: String): TimestampKey = {
    try {
      dataset.datasetConfig.tsFormat.get match {
        case "epoch" => TimestampKey(isValid = true, addTimeZone(dataset, new DateTime(value.toLong)).asInstanceOf[AnyRef])
        case _ =>
          val dtf = DateTimeFormat.forPattern(dataset.datasetConfig.tsFormat.get)
          TimestampKey(isValid = true, addTimeZone(dataset, dtf.parseDateTime(value)).asInstanceOf[AnyRef])
      }
    } catch {
      case _: Exception => TimestampKey(isValid = false, null)
    }
  }

  private def addTimeZone(dataset: Dataset, dateTime: DateTime): Long = {
    if (dataset.datasetConfig.datasetTimezone.isDefined) {
      val tz = DateTimeZone.forTimeZone(TimeZone.getTimeZone(dataset.datasetConfig.datasetTimezone.get))
      val offsetInMilliseconds = tz.getOffset(dateTime)
      dateTime.plusMillis(offsetInMilliseconds).getMillis
    } else {
      dateTime.getMillis
    }
  }
}
