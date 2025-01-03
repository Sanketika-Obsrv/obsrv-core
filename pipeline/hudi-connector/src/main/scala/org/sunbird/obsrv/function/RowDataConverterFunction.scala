package org.sunbird.obsrv.function

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.metrics.{Counter, SimpleCounter}
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.util.{HudiSchemaParser, HudiSchemaSpec}
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.streaming.HudiConnectorConfig

import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig, datasetId: String) extends RichMapFunction[MMap[String, AnyRef], RowData] {

  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  private var inputEventCount: Counter = _
  private var failedEventCount: Counter = _

  var jsonToRowDataConverters: JsonToRowDataConverters = _
  var objectMapper: ObjectMapper = _
  var hudiSchemaParser: HudiSchemaParser = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)

    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.SQL)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()

    // Register Flink metrics for inputEventCount and failedEventCount
    inputEventCount = getRuntimeContext.getMetricGroup
      .addGroup(config.jobName)
      .addGroup(datasetId)
      .counter(config.inputEventCountMetric)

    failedEventCount = getRuntimeContext.getMetricGroup
      .addGroup(config.jobName)
      .addGroup(datasetId)
      .counter(config.failedEventCountMetric)
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    try {
      println("======= map method..")
      println(s"Event size: ${event.size}")

      if (event.nonEmpty) {
        inputEventCount.inc(event.size)  // Increment by the event size
      }
      val rowData = convertToRowData(event)
      println(s"Current inputEventCount: ${inputEventCount.getCount}")

      rowData
    } catch {
      case ex: Exception =>
        failedEventCount.inc()
        logger.error("Failed to process record", ex)
        throw ex
    }
  }

  def convertToRowData(data: MMap[String, AnyRef]): RowData = {
    val eventJson = JSONUtil.serialize(data)
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    val rowType = hudiSchemaParser.rowTypeMap(datasetId)
    val converter: JsonToRowDataConverters.JsonToRowDataConverter = jsonToRowDataConverters.createRowConverter(rowType)
    val rowData = converter.convert(objectMapper.readTree(JSONUtil.serialize(flattenedData))).asInstanceOf[RowData]
    rowData
  }

  // Custom method to retrieve the metric values
  def getMetrics: Map[String, Long] = {
    Map(
      "input-event-count" -> inputEventCount.getCount,
      "failed-event-count" -> failedEventCount.getCount
    )
  }
}
