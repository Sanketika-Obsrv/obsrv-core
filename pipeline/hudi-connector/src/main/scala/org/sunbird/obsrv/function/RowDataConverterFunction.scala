package org.sunbird.obsrv.function

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.scala.metrics.ScalaGauge
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.metrics.SimpleCounter
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.obsrv.util.{HudiSchemaParser, HudiSchemaSpec}
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.streaming.{JobMetrics, Metrics, MetricsList}
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.streaming.HudiConnectorConfig

import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig, datasetId: String) extends RichMapFunction[MMap[String, AnyRef], RowData] with JobMetrics {

  // Variables for converters and object mapper
  var jsonToRowDataConverters: JsonToRowDataConverters = _
  var objectMapper: ObjectMapper = _
  var hudiSchemaParser: HudiSchemaParser = _

  // Logger for logging
  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  private val metricsList: MetricsList = getMetricsList()
  private val metrics: Metrics = registerMetrics(metricsList.datasets, metricsList.metrics)



  // Metrics for time tracking
  private var startTime: Long = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)

    // Initialize required converters and parsers
    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.SQL)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()

    // Register metrics
    getRuntimeContext.getMetricGroup.addGroup(config.jobName).addGroup(datasetId)
      .counter(config.inputEventCountMetric, new SimpleCounter())
    getRuntimeContext.getMetricGroup.addGroup(config.jobName).addGroup(datasetId)
      .counter(config.failedEventCountMetric, new SimpleCounter())
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    startTime = System.currentTimeMillis()
    metrics.incCounter(datasetId, config.inputEventCountMetric)
    try {
      metrics.incCounter(datasetId, config.inputEventCountMetric, event.size)
      val rowData = convertToRowData(event)
      rowData
    } catch {
      case ex: Exception =>
        metrics.incCounter(datasetId, config.failedEventCountMetric)
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

  def getMetricsList(): MetricsList = {
    MetricsList(
      datasets = List(datasetId),
      metrics = List(config.inputEventCountMetric, config.failedEventCountMetric)
    )
  }
}
