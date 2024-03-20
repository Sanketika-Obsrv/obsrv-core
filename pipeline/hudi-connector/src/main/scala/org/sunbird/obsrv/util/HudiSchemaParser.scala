package org.sunbird.obsrv.util

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.github.classgraph.{ClassGraph, Resource}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.JSONUtil

import java.nio.charset.StandardCharsets
import scala.collection.mutable


case class HudiSchemaSpec(dataset: String, schema: Schema, inputFormat: InputFormat)
case class Schema(table: String, partitionColumn: String, timestampColumn: String, primaryKey: String, columnSpec: List[ColumnSpec])
case class ColumnSpec(column: String, `type`: String)
case class InputFormat(`type`: String, flattenSpec: Option[JsonFlattenSpec] = None, columns: Option[List[String]] = None)
case class JsonFlattenSpec(fields: List[JsonFieldParserSpec])
case class JsonFieldParserSpec(`type`: String, name: String, expr: Option[String] = None)

class HudiSchemaParser {

  private val logger = LoggerFactory.getLogger(classOf[HudiSchemaParser])

  val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)
  val hudiSchemaMap: mutable.HashMap[String, HudiSchemaSpec] = {
    readSchema("schemas")
  }

  def readSchema(schemaUrl: String): mutable.HashMap[String, HudiSchemaSpec] = {
    val classGraphResult = new ClassGraph().acceptPaths(schemaUrl).scan()
    val schemaMap = new mutable.HashMap[String, HudiSchemaSpec]()
    try {
      val resources = classGraphResult.getResourcesWithExtension("json")
      resources.forEachByteArrayIgnoringIOException((res: Resource, content: Array[Byte]) => {
          val hudiSchemaSpec = JSONUtil.deserialize[HudiSchemaSpec](new String(content, StandardCharsets.UTF_8))
          schemaMap.put(hudiSchemaSpec.dataset, hudiSchemaSpec)
        })
    } finally {
      classGraphResult.close()
    }
    schemaMap
  }

  def parseJson(dataset: String, event: String): mutable.Map[String, Any] = {
    val parserSpec = hudiSchemaMap.get(dataset)
    val jsonNode = objectMapper.readTree(event)
    val flattenedEventData = mutable.Map[String, Any]()
    parserSpec.map { spec =>
      val columnSpec = spec.schema.columnSpec
      spec.inputFormat.flattenSpec.map {
        flattenSpec =>
          flattenSpec.fields.map {
            field =>
              val node = retrieveFieldFromJson(jsonNode, field)
              node.map {
                nodeValue =>
                  val fieldDataType = columnSpec.filter(_.column.equalsIgnoreCase(field.name)).head.`type`
                  val fieldValue = fieldDataType match {
                    case "string" => objectMapper.treeToValue(nodeValue, classOf[String])
                    case "int" => objectMapper.treeToValue(nodeValue, classOf[Int])
                    case "long" => objectMapper.treeToValue(nodeValue, classOf[Long])
                    case "double" => objectMapper.treeToValue(nodeValue, classOf[Double])
                    case _ => objectMapper.treeToValue(nodeValue, classOf[String])
                  }
                  flattenedEventData.put(field.name, fieldValue)
              }
          }
      }
    }
    flattenedEventData
  }

  def retrieveFieldFromJson(jsonNode: JsonNode, field: JsonFieldParserSpec): Option[JsonNode] = {
    if (field.`type`.equalsIgnoreCase("path")) {
      field.expr.map{ f => jsonNode.at(s"/${f.split("\\.").tail.mkString("/")}") }
    } else {
      Option(jsonNode.get(field.name))
    }
  }
}
