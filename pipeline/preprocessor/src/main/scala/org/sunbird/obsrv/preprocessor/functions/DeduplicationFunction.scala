package org.sunbird.obsrv.preprocessor.functions

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.cache._
import org.sunbird.obsrv.core.model.{ErrorConstants, Producer}
import org.sunbird.obsrv.core.streaming._
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.preprocessor.task.PipelinePreprocessorConfig
import org.sunbird.obsrv.streaming.BaseDatasetProcessFunction

import scala.collection.mutable

class DeduplicationFunction(config: PipelinePreprocessorConfig)(implicit val eventTypeInfo: TypeInformation[mutable.Map[String, AnyRef]])
  extends BaseDatasetProcessFunction(config) {

  @transient private var dedupEngine: DedupEngine = null

  override def getMetrics(): List[String] = {
    List(config.duplicationTotalMetricsCount, config.duplicationSkippedEventMetricsCount, config.duplicationEventMetricsCount, config.duplicationProcessedEventMetricsCount)
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    val redisConnect = new RedisConnect(config.redisHost, config.redisPort, config.redisConnectionTimeout)
    dedupEngine = new DedupEngine(redisConnect, config.dedupStore, config.cacheExpirySeconds)
  }

  override def close(): Unit = {
    super.close()
    dedupEngine.closeConnectionPool()
  }

  override def processElement(dataset: Dataset, msg: mutable.Map[String, AnyRef],
                              context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {

    metrics.incCounter(dataset.id, config.duplicationTotalMetricsCount)
    val dedupConfig = dataset.dedupConfig
    if (dedupConfig.isDefined && dedupConfig.get.dropDuplicates.get) {
      val event = msg(config.CONST_EVENT).asInstanceOf[Map[String, AnyRef]]
      val eventAsText = JSONUtil.serialize(event)
      val isDup = isDuplicate(dataset.id, dedupConfig.get.dedupKey, eventAsText, context, config)(dedupEngine)
      if (isDup) {
        metrics.incCounter(dataset.id, config.duplicationEventMetricsCount)
        context.output(config.duplicateEventsOutputTag, markFailed(msg, ErrorConstants.DUPLICATE_EVENT_FOUND, Producer.dedup))
      } else {
        metrics.incCounter(dataset.id, config.duplicationProcessedEventMetricsCount)
        context.output(config.uniqueEventsOutputTag, markSuccess(msg, Producer.dedup))
      }
    } else {
      metrics.incCounter(dataset.id, config.duplicationSkippedEventMetricsCount)
      context.output(config.uniqueEventsOutputTag, msg)
    }
  }

}
