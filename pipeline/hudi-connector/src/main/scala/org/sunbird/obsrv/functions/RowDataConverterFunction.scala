package org.sunbird.obsrv.functions

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.obsrv.util.{HudiSchemaParser, HudiSchemaSpec}
import org.apache.flink.table.data.RowData
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.streaming.HudiConnectorConfig
import scala.collection.mutable.{Map => MMap}

class RowDataConverterFunction(config: HudiConnectorConfig, datasetId: String, datasetType: String) extends RichMapFunction[MMap[String, AnyRef], RowData] {

  var jsonToRowDataConverters: JsonToRowDataConverters = _
  var objectMapper: ObjectMapper = _
  var hudiSchemaParser: HudiSchemaParser = _

  private val logger = LoggerFactory.getLogger(classOf[RowDataConverterFunction])

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    jsonToRowDataConverters = new JsonToRowDataConverters(false, true, TimestampFormat.SQL)
    objectMapper = new ObjectMapper()
    hudiSchemaParser = new HudiSchemaParser()
  }

  override def map(event: MMap[String, AnyRef]): RowData = {
    convertToRowData(event)
  }

  def convertToRowData(data: MMap[String, AnyRef]): RowData = {
    val eventJson = if(datasetType.equals("dataset")) JSONUtil.serialize(data) else JSONUtil.serialize(data("event"))
//    val eventJson = JSONUtil.serialize(data)
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    val rowType = hudiSchemaParser.rowTypeMap(datasetId)
    val converter: JsonToRowDataConverters.JsonToRowDataConverter = jsonToRowDataConverters.createRowConverter(rowType)
    val rowData = converter.convert(objectMapper.readTree(JSONUtil.serialize(flattenedData))).asInstanceOf[RowData]
    rowData
  }

}

import org.apache.flink.api.common.functions.FilterFunction

class DatasetFilter(datasetId: String, datasetType: String) extends FilterFunction[MMap[String, AnyRef]] {
  override def filter(data: MMap[String, AnyRef]): Boolean = {
    if(datasetType.equals("dataset")) {
      true
    }
    else {
      data("dataset").toString.equals(datasetId)
    }
  }

}
