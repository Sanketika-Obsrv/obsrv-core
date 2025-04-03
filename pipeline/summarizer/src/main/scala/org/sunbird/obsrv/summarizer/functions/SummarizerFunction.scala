package org.sunbird.obsrv.summarizer.functions

import org.sunbird.obsrv.job.util.Metrics
import org.sunbird.obsrv.job.function.BaseKeyedProcessFunction

import org.sunbird.obsrv.summarizer.task.{SummarizerConfig, SummaryKey}

import org.sunbird.obsrv.core.streaming.{BaseFunction, JobMetrics}
import org.sunbird.obsrv.core.model.{EventID, Producer, StatusCode}

import scala.collection.mutable
import org.slf4j.LoggerFactory
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction


case class SummaryStatus(status: StatusCode.Value)

class SummarizerFunction(config: SummarizerConfig)
  extends BaseKeyedProcessFunction[
    SummaryKey,
    mutable.Map[String, AnyRef],
    mutable.Map[String, AnyRef]
  ] with JobMetrics with BaseFunction {

  private[this] val logger = LoggerFactory.getLogger(classOf[SummarizerFunction])
  // MapState to store Summary object state for the particular key
  @transient private var summaryState:  MapState[String, Long] = _
  // ValueState to store the timer in case of no END event
  @transient private var timerState:  ValueState[java.lang.Long] = _

  override def getMetrics(): List[String] = {
    List(config.totalEventCount, config.successEventCount, config.failedSummarizerCount, config.successSummarizerCount)
  }

  // create the state objects
  override def open(parameters: Configuration): Unit = {
    val summaryStateDescriptor: MapStateDescriptor[String, Long] = new MapStateDescriptor[String, Long]("summaryState", classOf[String], classOf[Long])
    summaryState = getRuntimeContext().getMapState(summaryStateDescriptor)
    val timerDescriptor: ValueStateDescriptor[java.lang.Long] = new ValueStateDescriptor[java.lang.Long]("timer-state", Types.LONG)
    timerState = getRuntimeContext().getState(timerDescriptor)
  }

  override def processElement(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit =
  {
    val result = process_event(event, context)
    metrics.incCounter(config.defaultDatasetID, config.totalEventCount)
//    event.put(config.CONST_EVENT, result.resultJson.extract[Map[String, AnyRef]])
    result.status match {
      case StatusCode.skipped =>
        metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
        context.output(config.summarizerEventsOutputTag, markSkipped(event, Producer.summarizer))
      case StatusCode.success =>
        metrics.incCounter(config.defaultDatasetID, config.successSummarizerCount)
        context.output(config.summarizerEventsOutputTag, markSuccess(event, Producer.summarizer))
    }
  }
  
  override def onTimer(timestamp: Long, context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#OnTimerContext, metrics: Metrics): Unit = {
    val event: mutable.Map[String, AnyRef] = mutable.Map("ets" -> Long.box(timestamp))
    endSession(event, context)
    timerState.clear()
  }

    // 
  private def process_event(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): SummaryStatus = {
    val pre_processed_event = preProcessSummarization(event)

    if (pre_processed_event) {
      val eid = event.getOrElse("eid", "").toString
      val edata = event.getOrElse("edata", mutable.Map[String, AnyRef]()).asInstanceOf[mutable.Map[String, AnyRef]]
      val eventType = edata.getOrElse("type", "").toString
      eid match {
        case "START" =>
          if (eventType == "app") {
            startSession(event, context)
          }
        case "END" =>
          if (eventType == "app") {
            endSession(event, context)
          }
        case _ =>
          updateSession(event)
      }
      // create success result
      SummaryStatus(StatusCode.success)
    }
    else {
      // create skipped result
      SummaryStatus(StatusCode.skipped)
    }
  }

  // remove AUDIT, SEARCH, LOG events
  private def preProcessSummarization(event: mutable.Map[String, AnyRef]): Boolean = {
    val valid_event_type = List(EventID.AUDIT.toString, EventID.SEARCH.toString, EventID.LOG.toString)
    val eid = event.getOrElse("eid", "").toString
    if (!valid_event_type.contains(eid)) {
      return true
    }
    false
  }

  // instantiate a new Summary object on START event
  private def startSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    // if another app START received when existing is not closed, close current one and start new
    if (summaryState.get("startTime") > 0) {
      endSession(event, context)
    }
    val ets = event.getOrElse("ets", "").asInstanceOf[Long]
    summaryState.put("startTime", ets)
    summaryState.put("prevEts", ets)
    val timer: Long = context.timerService().currentProcessingTime() + config.sessionBreakTime*60*1000
    context.timerService().registerProcessingTimeTimer(timer)
  }

  // update the Summary object with captured metrics, like IMPRESSION count, resume an INACTIVE session
  private def updateSession(event: mutable.Map[String, AnyRef]): Unit = {
    val ets = event.getOrElse("ets", "").asInstanceOf[Long]
    val timeDiff = ets - summaryState.get("prevEts")
    if (timeDiff <= config.idleTime*60*1000) {
      val timeSpent = summaryState.get("totalTimeSpent")
      summaryState.put("totalTimeSpent", timeSpent+timeDiff)
    }
    summaryState.put("prevEts", ets)
  }

  // finalize the Summary object on END event, or timer, and return from processing element stream
  private def endSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    val ets = event.getOrElse("ets", "").asInstanceOf[Long]
    if (summaryState.get("startTime") > 0) {
      val timeDiff = ets - summaryState.get("prevEts")
      if (timeDiff <= config.idleTime*60*1000) {
        val timeSpent = summaryState.get("totalTimeSpent")
        summaryState.put("totalTimeSpent", timeSpent+timeDiff)
      }
      summaryState.put("prevEts", ets)
      summaryState.put("endTime", ets)
    }
    cleanUpState(context)
  }

    // reset the timer and summary object state
  private def cleanUpState(context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    val timer: Long = timerState.value()
    context.timerService().deleteProcessingTimeTimer(timer)
    timerState.clear()
    summaryState.clear()
  }

}
