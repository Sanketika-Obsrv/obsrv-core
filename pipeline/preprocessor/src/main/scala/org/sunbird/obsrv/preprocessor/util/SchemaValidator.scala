package org.sunbird.obsrv.preprocessor.util

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.{JsonSchema, JsonSchemaFactory}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.preprocessor.task.PipelinePreprocessorConfig

import java.io.IOException
import scala.collection.mutable

case class Schema(loadingURI: String, pointer: String)

case class Instance(pointer: String)

case class ValidationMsg(level: String, schema: Schema, instance: Instance, domain: String, keyword: String, message: String, allowed: Option[String],
                         found: Option[String], expected: Option[List[String]], unwanted: Option[List[String]], required: Option[List[String]], missing: Option[List[String]])

class SchemaValidator(config: PipelinePreprocessorConfig) extends java.io.Serializable {

  private val serialVersionUID = 8780940932759659175L
  private[this] val logger = LoggerFactory.getLogger(classOf[SchemaValidator])
  private[this] val schemaMap = mutable.Map[String, (JsonSchema, Boolean)]()

  def loadDataSchemas(datasets: List[Dataset]) = {
    datasets.foreach(dataset => {
      if (dataset.jsonSchema.isDefined) {
        try {
          loadJsonSchema(dataset.id, dataset.jsonSchema.get)
        } catch {
          case ex: ObsrvException => schemaMap.put(dataset.id, (null, false))
        }
      }
    })
  }

  def loadDataSchema(dataset: Dataset) = {
    if (!schemaMap.contains(dataset.id) && dataset.jsonSchema.isDefined) {
      try {
        loadJsonSchema(dataset.id, dataset.jsonSchema.get)
      } catch {
        case ex: ObsrvException => schemaMap.put(dataset.id, (null, false))
      }
    }
  }

  private def loadJsonSchema(datasetId: String, jsonSchemaStr: String) = {
    val schemaFactory = JsonSchemaFactory.byDefault
    try {
      val jsonSchema = schemaFactory.getJsonSchema(JsonLoader.fromString(jsonSchemaStr))
      jsonSchema.validate(JSONUtil.convertValue(Map("pqr" -> "value"))) // Test validate to check if Schema is valid
      schemaMap.put(datasetId, (jsonSchema, true))
    } catch {
      case ex: Exception =>
        logger.error(s"SchemaValidator:loadJsonSchema() - Unable to parse the schema json for dataset: $datasetId", ex)
        throw new ObsrvException(ErrorConstants.INVALID_JSON_SCHEMA)
    }
  }

  def schemaFileExists(dataset: Dataset): Boolean = {

    if (dataset.jsonSchema.isEmpty) {
      throw new ObsrvException(ErrorConstants.JSON_SCHEMA_NOT_FOUND)
    }
    schemaMap.get(dataset.id).map(f => f._2).orElse(Some(false)).get
  }

  @throws[IOException]
  @throws[ProcessingException]
  def validate(datasetId: String, event: Map[String, AnyRef]): ProcessingReport = {
    schemaMap(datasetId)._1.validate(JSONUtil.convertValue(event))
  }

  def getValidationMessages(report: ProcessingReport): List[ValidationMsg] = {
    val buffer = mutable.Buffer[ValidationMsg]()
    report.forEach(processingMsg => {
      buffer.append(JSONUtil.deserialize[ValidationMsg](JSONUtil.serialize(processingMsg.asJson())))
    })
    buffer.toList
  }

}
// $COVERAGE-ON$
