package org.sunbird.obsrv.preprocessor.util

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.SpecVersion.VersionFlag
import com.networknt.schema._
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.Dataset

import java.io.IOException
import scala.collection.mutable

case class ValidationMsg(
                          `type`: String,
                          code: Option[String],
                          message: String,
                          instanceLocation: Option[String],
                          property: Option[String],
                          evaluationPath: Option[String],
                          schemaLocation: Option[String],
                          messageKey: Option[String],
                          arguments: Option[Seq[String]],
                          details: Option[Map[String, Any]]
                        )


class SchemaValidator() extends java.io.Serializable {

  private val serialVersionUID = 8780940932759659175L
  private[this] val logger = LoggerFactory.getLogger(classOf[SchemaValidator])
  private[this] val schemaMap = mutable.Map[String, (JsonSchema, Boolean)]()
  // This creates a schema factory that will use Draft 2020-12 as the default if $schema is not specified
  // in the schema data. If $schema is specified in the schema data then that schema dialect will be used
  // instead and this version is ignored.
  private[this] val schemaFactory = JsonSchemaFactory.getInstance(VersionFlag.V202012)

  def loadDataSchemas(datasets: List[Dataset]): Unit = {
    datasets.foreach(dataset => {
      if (dataset.jsonSchema.isDefined) {
        try {
          loadJsonSchema(dataset.id, dataset.jsonSchema.get)
        } catch {
          case _: ObsrvException => schemaMap.put(dataset.id, (null, false))
        }
      }
    })
  }

  def loadDataSchema(dataset: Dataset): Any = {
    if (!schemaMap.contains(dataset.id) && dataset.jsonSchema.isDefined) {
      try {
        loadJsonSchema(dataset.id, dataset.jsonSchema.get)
      } catch {
        case _: ObsrvException => schemaMap.put(dataset.id, (null, false))
      }
    }
  }

  private def loadJsonSchema(datasetId: String, jsonSchemaStr: String): Unit = {
    try {
      val schemaValidatorsConfig = SchemaValidatorsConfig.builder().build()
      val jsonSchema = schemaFactory.getSchema(jsonSchemaStr, schemaValidatorsConfig)
      jsonSchema.validate(convertToJsonNode(Map())) // Test validate to check if Schema is valid
      schemaMap.put(datasetId, (jsonSchema, true))
    } catch {
      case ex: Exception =>
        println(s"SchemaValidator:loadJsonSchema() - Unable to parse the schema json for dataset: $datasetId", ex)
        throw new ObsrvException(ErrorConstants.INVALID_JSON_SCHEMA)
    }
  }

  def schemaFileExists(dataset: Dataset): Boolean = {
    //println("schemaMap " + schemaMap)
    schemaMap.get(dataset.id).map(f => f._2).orElse(Some(false)).get
  }

  @throws[IOException]
  def validate(datasetId: String, event: Map[String, AnyRef]): Set[ValidationMessage] = {

    import scala.collection.JavaConverters._
    val schema = schemaMap.getOrElse(datasetId, throw new ObsrvException(ErrorConstants.JSON_SCHEMA_NOT_FOUND))._1
    val messages = schema.validate(convertToJsonNode(event)).asScala.toSet
    messages.foreach { message =>
      println(s"Type: ${message.getType}")
      println(s"Code: ${message.getCode}")
      println(s"Message: ${message.getMessage}")
      println(s"Instance Location: ${message.getInstanceLocation}")
      println(s"Property: ${message.getProperty}")
      println(s"Evaluation Path: ${message.getEvaluationPath}")
      println(s"Schema Location: ${message.getSchemaLocation}")
      println(s"Message Key: ${message.getMessageKey}")
      println(s"Arguments: ${JSONUtil.serialize(message.getArguments)}")
      println(s"Details: ${JSONUtil.serialize(message.getDetails)}")
      println("-" * 40) // Separator for readability
    }
    messages
  }

  def getValidationMessages(validationMessages: Set[ValidationMessage]): List[ValidationMsg] = {
    validationMessages.map { validationMessage =>
      JSONUtil.deserialize[ValidationMsg](JSONUtil.serialize(validationMessage))
    }.toList
  }

  private def convertToJsonNode(data: Map[String, AnyRef]): JsonNode = {
    JSONUtil.convertValue(data)
  }

}
// $COVERAGE-ON$
