package org.sunbird.obsrv.core.streaming

import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.cache.DedupEngine
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.Models._
import org.sunbird.obsrv.core.model._
import org.sunbird.obsrv.core.util.JSONUtil

import scala.collection.mutable

trait BaseDeduplication {

  private[this] val logger = LoggerFactory.getLogger(classOf[BaseDeduplication])

  def isDuplicate(datasetId: String, dedupKey: Option[String], event: String,
                  context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                  config: BaseJobConfig[_])
                 (implicit deDupEngine: DedupEngine): Boolean = {

    try {
      val key = datasetId + ":" + getDedupKey(dedupKey, event)
      if (!deDupEngine.isUniqueEvent(key)) {
        logger.debug(s"Event with mid: $key is duplicate")
        true
      } else {
        deDupEngine.storeChecksum(key)
        false
      }
    } catch {
      case ex: ObsrvException =>
        val sysEvent = JSONUtil.serialize(SystemEvent(
          EventID.METRIC,
          ctx = ContextData(module = ModuleID.processing, pdata = PData(config.jobName, PDataType.flink, Some(Producer.dedup)), dataset = Some(datasetId)),
          data = EData(error = Some(ErrorLog(pdata_id = Producer.dedup, pdata_status = StatusCode.skipped, error_type = FunctionalError.DedupFailed, error_code = ex.error.errorCode, error_message = ex.error.errorMsg, error_level = ErrorLevel.warn)))
        ))
        logger.warn("BaseDeduplication:isDuplicate() | Exception", sysEvent)
        context.output(config.systemEventsOutputTag, sysEvent)
        false
    }
  }

  private def getDedupKey(dedupKey: Option[String], event: String): String = {
    if (dedupKey.isEmpty) {
      throw new ObsrvException(ErrorConstants.NO_DEDUP_KEY_FOUND)
    }
    val node = JSONUtil.getKey(dedupKey.get, event)
    if (node.isMissingNode) {
      throw new ObsrvException(ErrorConstants.NO_DEDUP_KEY_FOUND)
    }
    if (!node.isTextual && !node.isNumber) {
      logger.warn(s"Dedup | Dedup key is not a string or number | dedupKey=$dedupKey | keyType=${node.getNodeType}")
      throw new ObsrvException(ErrorConstants.DEDUP_KEY_NOT_A_STRING_OR_NUMBER)
    }
    node.asText()
  }

}
