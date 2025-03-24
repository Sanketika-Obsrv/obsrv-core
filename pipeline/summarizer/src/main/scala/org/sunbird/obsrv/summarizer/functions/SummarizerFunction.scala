package org.sunbird.obsrv.summarizer.functions

import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants.Error
import org.sunbird.obsrv.core.model.FunctionalError.FunctionalError
import org.sunbird.obsrv.core.model.Models._
import org.sunbird.obsrv.core.model._
import org.sunbird.obsrv.core.streaming.{BaseKeyedProcessFunction, Metrics, MetricsList}
import org.sunbird.obsrv.core.util.Util.getMutableMap
import org.sunbird.obsrv.core.util.{JSONUtil, Util}
import org.sunbird.obsrv.summarizer.task.summarizerConfig

import org.slf4j.LoggerFactory
import scala.collection.mutable
import org.apache.flink.streaming.api.functions.KeyedProcessFunction

class SummarizerFunction(config: summarizerConfig)
  extends BaseKeyedProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[SummarizerFunction])

  override def getMetrics(): List[String] = {
    val metrics = List(config.totalEventCount, config.successEventCount, config.failedSummarizationCount, config.successSummarizationCount)
  }

  override def processElement(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[String, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {

    implicit val jsonFormats: Formats = DefaultFormats.withLong
    val result = preProcessSummarization(dataset, msg, config)
    metrics.incCounter(dataset.id, config.totalEventCount)
    msg.put(config.CONST_EVENT, result.resultJson.extract[Map[String, AnyRef]])
    result.status match {
      case StatusCode.failed =>
        metrics.incCounter(dataset.id, config.failedSummarizationCount)
        context.output(config.summarizerFailedEventOutputTag, markFailed(msg, ErrorConstants.ERR_SUMMARIZATION_FAILED, Producer.summarizer))
        logSystemEvents(dataset, msg, result, context)
      case StatusCode.success =>
        metrics.incCounter(dataset.id, config.successSummarizationCount)
        context.output(config.summarizerEventsOutputTag, markSuccess(msg, Producer.summarizer))
    }
  }
  
  override def onTimer(timestamp: Long, context: KeyedProcessFunction[String, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#OnTimerContext, out: Collector[Alert]): Unit = {
    timerState.clear()
    flagState.clear()
  }

  private def cleanUp(context: KeyedProcessFunction[String, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    val timer: Long = timerState.value()
    context.timerService().deleteProcessingTimeTimer(timer)
    timerState.clear()
    flagState.clear()
  }

  def preProcessSummarization()

  def startSession()

  def updateSession()

  def endSession()

  def processTotalTimeSpent()

  def processIdleTime()

  def processEventCount()

  def processSessionStatus()

}
