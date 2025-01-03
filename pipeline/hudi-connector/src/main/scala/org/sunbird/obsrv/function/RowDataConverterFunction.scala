package org.sunbird.obsrv.function

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.metrics.{Counter, Gauge}
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.streaming.HudiConnectorConfig
import org.sunbird.obsrv.util.HudiSchemaParser

import scala.collection.mutable.{Map => MMap}



import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.metrics.Gauge
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.streaming.HudiConnectorConfig
import org.sunbird.obsrv.util.HudiSchemaParser

import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig, datasetId: String)
  extends RichMapFunction[MMap[String, AnyRef], RowData] {

  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  private var metrics: Metrics = _
  private var jsonToRowDataConverters: JsonToRowDataConverters = _
  private var objectMapper: ObjectMapper = _
  private var hudiSchemaParser: HudiSchemaParser = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)

    metrics = new Metrics()
    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.SQL)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()

    // Register dynamic Gauge for inputEventCount
    getRuntimeContext.getMetricGroup
      .addGroup(config.jobName)
      .addGroup(datasetId)
      .gauge[Long, ScalaGauge[Long]]("input-data-count", ScalaGauge[Long](() =>
        metrics.getAndReset(datasetId, "input-data-count")
      ))

    // Register dynamic Gauge for failedEventCount
    getRuntimeContext.getMetricGroup
      .addGroup(config.jobName)
      .addGroup(datasetId)
      .gauge[Long, ScalaGauge[Long]]("failed-data-count", ScalaGauge[Long](() =>
        metrics.getAndReset(datasetId, "failed-data-count")
      ))
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    try {
      if (event.nonEmpty) {
        metrics.increment(datasetId, "input-data-count", 1)
      }
      val rowData = convertToRowData(event)
      rowData
    } catch {
      case ex: Exception =>
        metrics.increment(datasetId, "failed-data-count", 1)
        logger.error("Failed to process record", ex)
        throw ex
    }
  }

  def convertToRowData(data: MMap[String, AnyRef]): RowData = {
    val eventJson = JSONUtil.serialize(data)
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    val rowType = hudiSchemaParser.rowTypeMap(datasetId)
    val converter: JsonToRowDataConverters.JsonToRowDataConverter =
      jsonToRowDataConverters.createRowConverter(rowType)
    converter.convert(objectMapper.readTree(JSONUtil.serialize(flattenedData))).asInstanceOf[RowData]
  }
}


import scala.collection.concurrent.TrieMap

class Metrics {
  private val metricStore = TrieMap[(String, String), Long]()

  def increment(dataset: String, metric: String, value: Long): Unit = {
    metricStore.synchronized {
      val key = (dataset, metric)
      val current = metricStore.getOrElse(key, 0L)
      metricStore.put(key, current + value)
    }
  }

  def getAndReset(dataset: String, metric: String): Long = {
    metricStore.synchronized {
      val key = (dataset, metric)
      val current = metricStore.getOrElse(key, 0L)
      metricStore.remove(key)
      current
    }
  }
}


import org.apache.flink.metrics.Gauge

case class ScalaGauge[T](getValueFn: () => T) extends Gauge[T] {
  override def getValue: T = getValueFn()
}


