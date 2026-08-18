package org.sunbird.obsrv.function

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.streaming.HudiConnectorConfig
import org.sunbird.obsrv.util.{HMetrics, HudiSchemaParser, ScalaGauge}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig, datasetId: String)
  extends RichMapFunction[MMap[String, AnyRef], RowData] {

  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  private var metrics: HMetrics = _
  private var jsonToRowDataConverters: JsonToRowDataConverters = _
  private var objectMapper: ObjectMapper = _
  private var hudiSchemaParser: HudiSchemaParser = _
  // Was re-created on every single event inside convertToRowData() -
  // createRowConverter dynamically builds field converter closures via reflection across the
  // whole schema; doing that per-record instead of once is extreme allocation/GC overhead.
  // datasetId (and therefore rowType) is fixed for the lifetime of this instance, so build it
  // once here.
  private var rowConverter: JsonToRowDataConverters.JsonToRowDataConverter = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)

    metrics = new HMetrics()
    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.SQL)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()
    rowConverter = jsonToRowDataConverters.createRowConverter(hudiSchemaParser.rowTypeMap(datasetId))

    getRuntimeContext.getMetricGroup
      .addGroup(config.jobName)
      .addGroup(datasetId)
      .gauge[Long, ScalaGauge[Long]](config.inputEventCountMetric, ScalaGauge[Long](() =>
        metrics.getAndReset(datasetId, config.inputEventCountMetric)
      ))

    getRuntimeContext.getMetricGroup
      .addGroup(config.jobName)
      .addGroup(datasetId)
      .gauge[Long, ScalaGauge[Long]](config.failedEventCountMetric, ScalaGauge[Long](() =>
        metrics.getAndReset(datasetId, config.failedEventCountMetric)
      ))
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    try {
      if (event.nonEmpty) {
        metrics.increment(datasetId, config.inputEventCountMetric, 1)
      }
      val rowData = convertToRowData(event)
      rowData
    } catch {
      case ex: Exception =>
        metrics.increment(datasetId, config.failedEventCountMetric, 1)
        logger.error("Failed to process record", ex)
        throw ex
    }
  }

  def convertToRowData(data: MMap[String, AnyRef]): RowData = {
    val eventJson = JSONUtil.serialize(data)
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    // Was objectMapper.readTree(JSONUtil.serialize(flattenedData)) - serialized flattenedData
    // to a JSON string and immediately re-parsed it back into a JsonNode, on every event.
    // valueToTree goes straight from a Map to a JsonNode - still Jackson's internal object-
    // mapping machinery, but no intermediate JSON string allocation/parse. Needs a plain
    // java.util.Map (.asJava): this objectMapper is Flink's own shaded Jackson (the type
    // JsonToRowDataConverters actually requires), which has no DefaultScalaModule registered
    // and wouldn't introspect a raw Scala Map correctly - java.util.Map is a standard type any
    // ObjectMapper understands natively, Scala-aware or not.
    // Note: the earlier hop (data -> JSONUtil.serialize -> parseJson's own internal
    // objectMapper.readTree) still round-trips through a JSON string - removing that one too
    // would mean changing HudiSchemaParser.parseJson's signature to accept a JsonNode
    // directly instead of a String, a larger API change left for a follow-up.
    val flattenedNode = objectMapper.valueToTree[org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode](flattenedData.asJava)
    rowConverter.convert(flattenedNode).asInstanceOf[RowData]
  }
}