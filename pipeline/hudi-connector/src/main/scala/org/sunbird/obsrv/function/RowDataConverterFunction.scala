package org.sunbird.obsrv.function

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.metrics.{Counter, SimpleCounter}
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.obsrv.util.{HudiSchemaParser, HudiSchemaSpec}
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.streaming.HudiConnectorConfig

import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig, datasetId: String) extends RichMapFunction[MMap[String, AnyRef], RowData] {

  // Logger for logging
  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  // Initialize counters for input and failed events
  private var inputEventCount: Counter = _
  private var failedEventCount: Counter = _

  // Variables for converters and object mapper
  var jsonToRowDataConverters: JsonToRowDataConverters = _
  var objectMapper: ObjectMapper = _
  var hudiSchemaParser: HudiSchemaParser = _

  // Initialize the start time
  private var startTime: Long = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)

    // Initialize required converters and parsers
    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.SQL)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()

    // Register Flink metrics for inputEventCount and failedEventCount
    inputEventCount = getRuntimeContext.getMetricGroup.counter("input-event-count")
    failedEventCount = getRuntimeContext.getMetricGroup.counter("input-event-count")
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    startTime = System.currentTimeMillis()

    try {
      inputEventCount.inc(event.size)
      val rowData = convertToRowData(event)
      rowData
    } catch {
      case ex: Exception =>
        // Increment the failed event count in case of failure
        failedEventCount.inc()
        logger.error("Failed to process record", ex)
        throw ex
    }
  }

  // Method to convert event data into RowData
  def convertToRowData(data: MMap[String, AnyRef]): RowData = {
    val eventJson = JSONUtil.serialize(data)
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    val rowType = hudiSchemaParser.rowTypeMap(datasetId)
    val converter: JsonToRowDataConverters.JsonToRowDataConverter = jsonToRowDataConverters.createRowConverter(rowType)
    val rowData = converter.convert(objectMapper.readTree(JSONUtil.serialize(flattenedData))).asInstanceOf[RowData]
    rowData
  }

  // Custom method to retrieve the metric values (e.g., for exposing them in a monitoring system)
  def getMetrics: Map[String, Long] = {
    Map(
      "input-event-count" -> inputEventCount.getCount,
      "failed-event-count" -> failedEventCount.getCount
    )
  }
}
