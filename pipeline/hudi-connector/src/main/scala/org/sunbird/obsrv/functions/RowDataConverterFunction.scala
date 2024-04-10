package org.sunbird.obsrv.functions

import com.fasterxml.jackson.databind.JsonNode
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.obsrv.util.{HudiSchemaParser, HudiSchemaSpec}
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.streaming.HudiConnectorConfig
import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig) extends RichMapFunction[MMap[String, AnyRef], RowData] {

  var jsonToRowDataConverters: JsonToRowDataConverters = _
  var objectMapper: ObjectMapper = _
  var hudiSchemaParser: HudiSchemaParser = _

  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.ISO_8601)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    convertToRowData(event)
  }

  def convertToRowData(data: MMap[String, AnyRef]): RowData = {
    val event = data("event")//.asInstanceOf[MMap[String, AnyRef]]
    val eventJson = JSONUtil.serialize(event)
    val datasetId = data.get("dataset").get.asInstanceOf[String]
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    val rowType = hudiSchemaParser.rowTypeMap(datasetId)
    val converter: JsonToRowDataConverters.JsonToRowDataConverter = jsonToRowDataConverters.createRowConverter(rowType)
    val rowData = converter.convert(objectMapper.readTree(JSONUtil.serialize(flattenedData))).asInstanceOf[RowData]
    rowData
  }

}
