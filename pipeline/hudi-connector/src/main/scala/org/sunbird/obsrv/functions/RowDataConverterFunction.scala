package org.sunbird.obsrv.functions

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.obsrv.util.{HudiSchemaParser, HudiSchemaSpec}
import org.apache.flink.table.data.RowData
import org.apache.flink.table.types.logical.{LogicalType, RowType}
import org.apache.hudi.configuration.FlinkOptions
import org.apache.hudi.util.AvroSchemaConverter
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.{ErrorConstants, FunctionalError}
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.streaming.HudiConnectorConfig

import scala.collection.mutable
import scala.collection.mutable.{Map => MMap}

// case class RowDataType(fields: Array[String], fieldTypes: Array[LogicalType])

class RowDataConverterFunction() extends RichMapFunction[MMap[String, AnyRef], RowData] {

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
    val event = data("event").asInstanceOf[Map[String, AnyRef]]
    val eventJson = JSONUtil.serialize(event)
    // fetch datasource config from datasetId
    val datasetIdOpt = data.get("dataset")
    if (datasetIdOpt.isEmpty) {
      // if dataset is missing in event, log its invalid and move it to failed topic
    }
    val datasetId = datasetIdOpt.get.asInstanceOf[String]
    val flattenedData = hudiSchemaParser.parseJson(datasetId, eventJson)
    val rowType = hudiSchemaParser.rowTypeMap(datasetId)
    val converter: JsonToRowDataConverters.JsonToRowDataConverter = jsonToRowDataConverters.createRowConverter(rowType)
    val rowData = converter.convert(objectMapper.readTree(JSONUtil.serialize(flattenedData))).asInstanceOf[RowData]
    rowData
  }

  /*
  def retrieveEventFields(data: MMap[String, Any]): RowDataType = {
    val fields = mutable.SortedMap[String, LogicalType]()
    data.keySet.foreach {
      field =>
        val fieldType = data(field) match {
          case _: String => new VarCharType()
          case _: Double => new DoubleType()
          case _: Long => new BigIntType()
          case _: Int => new IntType()
          case _: Boolean => new BooleanType()
          case _: MMap[String, String] => new MapType(new VarCharType(), new VarCharType())
          case _: Map[String, String] => new MapType(new VarCharType(), new VarCharType())
        }
        fields.put(field, fieldType)
    }
    RowDataType(fields.keySet.toArray, fields.values.toArray)
  }
 */
}
