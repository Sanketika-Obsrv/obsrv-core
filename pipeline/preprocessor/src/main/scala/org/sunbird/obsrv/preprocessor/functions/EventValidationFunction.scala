package org.sunbird.obsrv.preprocessor.functions

import com.github.fge.jsonschema.core.report.ProcessingReport
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.FunctionalError.FunctionalError
import org.sunbird.obsrv.core.model.Models._
import org.sunbird.obsrv.core.model._
import org.sunbird.obsrv.core.streaming.Metrics
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.model.{DatasetStatus, ValidationMode}
import org.sunbird.obsrv.preprocessor.task.PipelinePreprocessorConfig
import org.sunbird.obsrv.preprocessor.util.{SchemaValidator, ValidationMsg}
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.streaming.BaseDatasetProcessFunction

import java.lang
import scala.collection.mutable

class EventValidationFunction(config: PipelinePreprocessorConfig, @transient var schemaValidator: SchemaValidator = null)
                             (implicit val eventTypeInfo: TypeInformation[mutable.Map[String, AnyRef]])
  extends BaseDatasetProcessFunction(config) {
  private[this] val logger = LoggerFactory.getLogger(classOf[EventValidationFunction])

  override def getMetrics(): List[String] = {
    List(config.validationTotalMetricsCount, config.validationFailureMetricsCount, config.validationSuccessMetricsCount,
      config.validationSkipMetricsCount, config.eventIgnoredMetricsCount)
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    if (schemaValidator == null) {
      schemaValidator = new SchemaValidator(config)
      schemaValidator.loadDataSchemas(DatasetRegistry.getAllDatasets(config.datasetType()))
    }
  }

  override def close(): Unit = {
    super.close()
  }

  override def processElement(dataset: Dataset, msg: mutable.Map[String, AnyRef],
                              ctx: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {

    metrics.incCounter(config.defaultDatasetID, config.validationTotalMetricsCount)
    if (dataset.status != DatasetStatus.Live) {
      metrics.incCounter(dataset.id, config.eventIgnoredMetricsCount)
      return
    }
    val validationConfig = dataset.validationConfig
    if (validationConfig.isDefined && validationConfig.get.validate.get) {
      schemaValidator.loadDataSchema(dataset)
      validateEvent(dataset, msg, ctx, metrics)
    } else {
      metrics.incCounter(dataset.id, config.validationSkipMetricsCount)
      ctx.output(config.validEventsOutputTag, markSkipped(msg, Producer.validator))
    }
  }

  private def validateEvent(dataset: Dataset, msg: mutable.Map[String, AnyRef],
                            ctx: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                            metrics: Metrics): Unit = {

    val event = msg(config.CONST_EVENT).asInstanceOf[Map[String, AnyRef]]
    if (schemaValidator.schemaFileExists(dataset)) {
      val validationReport = schemaValidator.validate(dataset.id, event)
      onValidationResult(dataset, msg, metrics, ctx, validationReport)
    }
  }

  private def onValidationResult(dataset: Dataset, event: mutable.Map[String, AnyRef], metrics: Metrics,
                                 ctx: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                                 validationReport: ProcessingReport): Unit = {
    if (validationReport.isSuccess) {
      validationSuccess(dataset, event, metrics, ctx)
    } else {
      val validationFailureMsgs = schemaValidator.getValidationMessages(report = validationReport)
      val validationFailureCount = validationFailureMsgs.size
      val additionalFieldsCount = validationFailureMsgs.count(f => "additionalProperties".equals(f.keyword))
      if (validationFailureCount == additionalFieldsCount) {
        dataset.validationConfig.get.mode.get match {
          case ValidationMode.Strict =>
            validationFailure(dataset, event, metrics, ctx, validationFailureMsgs)
          case ValidationMode.IgnoreNewFields =>
            validationSuccess(dataset, event, metrics, ctx)
          case ValidationMode.DiscardNewFields =>
            // TODO: [P2] Write logic to discard the fields from the pipeline. Fields are anyway discarded from Druid but not from data lake
            validationSuccess(dataset, event, metrics, ctx)
        }
      } else {
        validationFailure(dataset, event, metrics, ctx, validationFailureMsgs)
      }
    }
  }

  private def getSystemEvent(dataset: Dataset, functionalError: FunctionalError, failedCount: Int): String = {
    JSONUtil.serialize(SystemEvent(EventID.METRIC,
      ctx = ContextData(module = ModuleID.processing, pdata = PData(config.jobName, PDataType.flink, Some(Producer.validator)), dataset = Some(dataset.id)),
      data = EData(
        error = Some(ErrorLog(pdata_id = Producer.validator, pdata_status = StatusCode.failed, error_type = functionalError, error_code = ErrorConstants.SCHEMA_VALIDATION_FAILED.errorCode, error_message = ErrorConstants.SCHEMA_VALIDATION_FAILED.errorMsg, error_level = ErrorLevel.warn)),
        pipeline_stats = Some(PipelineStats(validation_errors = Some(failedCount)))
      )
    ))
  }

  private def generateSystemEvents(dataset: Dataset, validationFailureMsgs: List[ValidationMsg], context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {

    val reqFailedCount = validationFailureMsgs.count(f => "required".equals(f.keyword))
    val typeFailedCount = validationFailureMsgs.count(f => "type".equals(f.keyword))
    val unknownFailureCount = validationFailureMsgs.count(f => !("type".equals(f.keyword) && "required".equals(f.keyword) && "additionalProperties".equals(f.keyword)))
    if (reqFailedCount > 0) {
      context.output(config.systemEventsOutputTag, getSystemEvent(dataset, FunctionalError.RequiredFieldsMissing, reqFailedCount))
    }
    if (typeFailedCount > 0) {
      context.output(config.systemEventsOutputTag, getSystemEvent(dataset, FunctionalError.DataTypeMismatch, typeFailedCount))
    }
    if (unknownFailureCount > 0) {
      context.output(config.systemEventsOutputTag, getSystemEvent(dataset, FunctionalError.UnknownValidationError, unknownFailureCount))
    }

    // Log the validation failure messages
    validationFailureMsgs.foreach(_ => (msg: ValidationMsg) => {
      msg.keyword match {
        case "additionalProperties" =>
          logger.warn(s"SchemaValidator | Additional properties found | dataset=${dataset.id} | ValidationMessage=${JSONUtil.serialize(msg)}")
        case "required" =>
          logger.error(s"SchemaValidator | Required Fields Missing | dataset=${dataset.id} | ValidationMessage=${JSONUtil.serialize(msg)}")
        case "type" =>
          logger.error(s"SchemaValidator | Data type mismatch found | dataset=${dataset.id} | ValidationMessage=${JSONUtil.serialize(msg)}")
        case _ =>
          logger.warn(s"SchemaValidator | Unknown Validation errors found | dataset=${dataset.id} | ValidationMessage=${JSONUtil.serialize(msg)}")
      }
    })
  }

  private def validationSuccess(dataset: Dataset, event: mutable.Map[String, AnyRef], metrics: Metrics,
                                context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    metrics.incCounter(dataset.id, config.validationSuccessMetricsCount)
    context.output(config.validEventsOutputTag, markSuccess(event, Producer.validator))
  }

  private def validationFailure(dataset: Dataset, event: mutable.Map[String, AnyRef], metrics: Metrics,
                                context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                                validationFailureMsgs: List[ValidationMsg]): Unit = {
    metrics.incCounter(dataset.id, config.validationFailureMetricsCount)
    context.output(config.invalidEventsOutputTag, markFailed(event, ErrorConstants.SCHEMA_VALIDATION_FAILED, Producer.validator))
    generateSystemEvents(dataset, validationFailureMsgs, context)
  }

}