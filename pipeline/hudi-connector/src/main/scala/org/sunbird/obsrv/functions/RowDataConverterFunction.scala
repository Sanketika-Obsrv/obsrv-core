package org.sunbird.obsrv.functions

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.formats.common.TimestampFormat
import org.apache.flink.formats.json.JsonToRowDataConverters
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.obsrv.util.HudiSchemaParser
import org.apache.flink.table.data.RowData
import org.apache.flink.table.types.logical.LogicalType
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.JSONUtil

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
    convertToRowData(event("event").asInstanceOf[Map[String, AnyRef]])
  }

  def convertToRowData(event: Map[String, AnyRef]): RowData = {
    val eventJson = JSONUtil.serialize(event)
    val flattenedData = hudiSchemaParser.parseJson("financial_transactions", eventJson)
    val rowType = hudiSchemaParser.rowTypeMap("financial_transactions")
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
