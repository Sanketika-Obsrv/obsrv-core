package org.sunbird.obsrv.core.model

import org.sunbird.obsrv.core.model.ErrorLevel.ErrorLevel
import org.sunbird.obsrv.core.model.EventID.EventID
import org.sunbird.obsrv.core.model.FunctionalError.FunctionalError
import org.sunbird.obsrv.core.model.ModuleID.ModuleID
import org.sunbird.obsrv.core.model.PDataType.PDataType
import org.sunbird.obsrv.core.model.Producer.Producer
import org.sunbird.obsrv.core.model.StatusCode.StatusCode

object Models {

  case class PData(id: String, `type`: PDataType, pid: Option[Producer])

  case class ContextData(module: ModuleID, pdata: PData, dataset: Option[String] = None, eid: Option[String] = None)

  case class ErrorLog(pdata_id: Producer, pdata_status: StatusCode, error_type: FunctionalError, error_code: String, error_message: String, error_level: ErrorLevel)

  case class PipelineStats(validation_errors: Option[Int] = None, extractor_events: Option[Int] = None, extractor_status: Option[StatusCode] = None,
                           extractor_time: Option[Long] = None, validator_status: Option[StatusCode] = None, validator_time: Option[Long] = None,
                           dedup_status: Option[StatusCode] = None, dedup_time: Option[Long] = None, denorm_status: Option[StatusCode] = None,
                           denorm_time: Option[Long] = None, transform_status: Option[StatusCode] = None, transform_time: Option[Long] = None,
                           total_processing_time: Option[Long] = None, latency_time: Option[Long] = None, processing_time: Option[Long] = None)

  case class EData(error: Option[ErrorLog] = None, pipeline_stats: Option[PipelineStats] = None, extra: Option[Map[String, AnyRef]] = None)

  case class SystemEvent(etype: EventID, ctx: ContextData, data: EData, ets: Long = System.currentTimeMillis())
}

object EventID extends Enumeration {
  type EventID = Value
  val LOG, METRIC = Value
}

object ErrorLevel extends Enumeration {
  type ErrorLevel = Value
  val debug, info, warn, critical = Value
}

object FunctionalError extends Enumeration {
  type FunctionalError = Value
  val DedupFailed, RequiredFieldsMissing, DataTypeMismatch, UnknownValidationError, MissingDatasetId, MissingEventData, MissingTimestampKey,
  EventSizeExceeded, ExtractionDataFormatInvalid, DenormKeyMissing, DenormKeyInvalid, DenormDataNotFound = Value
}

object Producer extends Enumeration {
  type Producer = Value
  val extractor, dedup, validator, denorm, transformer, router, masterdataprocessor = Value
}

object ModuleID extends Enumeration {
  type ModuleID = Value
  val ingestion, processing, storage, query = Value
}

object StatusCode extends Enumeration {
  type StatusCode = Value
  val success, failed, skipped, partial = Value
}

object PDataType extends Enumeration {
  type PDataType = Value
  val flink, spark, druid, kafka, api = Value
}

object Stats extends Enumeration {
  type Stats = Value
  val total_processing_time, latency_time, processing_time = Value
}