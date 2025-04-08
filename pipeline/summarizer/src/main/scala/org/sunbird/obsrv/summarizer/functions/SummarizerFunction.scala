package org.sunbird.obsrv.summarizer.functions

import org.sunbird.obsrv.job.function.BaseKeyedProcessFunction

import org.sunbird.obsrv.summarizer.task.{SummarizerConfig, SummaryKey}

import org.sunbird.obsrv.job.util.Metrics
import org.sunbird.obsrv.job.model.{EventID, Producer, StatusCode}

import scala.collection.mutable
import org.slf4j.LoggerFactory
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction

case class SummaryStatus(status: StatusCode.Value, summary: mutable.Map[String, AnyRef])

class SummarizerFunction(config: SummarizerConfig)
  extends BaseKeyedProcessFunction[
    SummaryKey,
    mutable.Map[String, AnyRef],
    mutable.Map[String, AnyRef]
  ] {

  private[this] val logger = LoggerFactory.getLogger(classOf[SummarizerFunction])
  // MapState to store String values Summary object state for the particular key
  @transient private var summaryStateString:  MapState[String, String] = _
  // MapState to store Long values Summary object state for the particular key
  @transient private var summaryStateLong:  MapState[String, Long] = _
  // ValueState to store the timer in case of no END event
  @transient private var timerState:  ValueState[java.lang.Long] = _

  override def getMetrics(): List[String] = {
    List(config.totalEventCount, config.successEventCount, config.failedSummarizerCount, config.successSummarizerCount, config.skippedSummarizerCount)
  }

  // create the state objects
  override def open(parameters: Configuration): Unit = {
    // does not allow to create any ref as value type for mapstatedescriptor. hence creating two separate states
    val summaryStateStringDescriptor: MapStateDescriptor[String, String] = new MapStateDescriptor[String, String]("summaryState", classOf[String], classOf[String])
    summaryStateString = getRuntimeContext().getMapState(summaryStateStringDescriptor)
    val summaryStateLongDescriptor: MapStateDescriptor[String, Long] = new MapStateDescriptor[String, Long]("summaryState", classOf[String], classOf[Long])
    summaryStateLong = getRuntimeContext().getMapState(summaryStateLongDescriptor)
    // to store timer state
    val timerDescriptor: ValueStateDescriptor[java.lang.Long] = new ValueStateDescriptor[java.lang.Long]("timer-state", Types.LONG)
    timerState = getRuntimeContext().getState(timerDescriptor)
  }

  override def processElement(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit =
  {
    initMetrics("FlinkKafkaConnector", config.jobName)
    val result = process_event(event, context)
    metrics.incCounter(config.defaultDatasetID, config.totalEventCount)
    result.status match {
      case StatusCode.skipped =>
        metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
        context.output(config.summarizerEventsOutputTag, markSkipped(result.summary, Producer.summarizer))
      case StatusCode.success =>
        metrics.incCounter(config.defaultDatasetID, config.successSummarizerCount)
        context.output(config.summarizerEventsOutputTag, markSuccess(result.summary, Producer.summarizer))
    }
  }

  // triggered in case no summary state not closed by END event in time
  override def onTimer(timestamp: Long, context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#OnTimerContext, metrics: Metrics): Unit = {
    val event: mutable.Map[String, AnyRef] = mutable.Map("ets" -> Long.box(timestamp))
    endSession(event, context)
    timerState.clear()
  }

  // process the telemetry event
  private def process_event(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): SummaryStatus = {

    // check if eid in list
    val pre_processed_event = preProcessSummarization(event)
    // create mutable result event
    if (pre_processed_event) {
      val eid = event.getOrElse("eid", "").toString
      val edata = event.get("edata") match {
        case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
        case _ => mutable.Map[String, AnyRef]()
      }
      val eventType = edata.getOrElse("type", "").toString
      eid match {
        case "START" =>
          if (eventType == "app") {
            startSession(event, context)
          }
        case "END" =>
          if (eventType == "app") {
            endSession(event, context)
            val result_event = createSummaryEvent(event)
            return SummaryStatus(StatusCode.success, result_event)
          }
        case _ =>
          updateSession(event, context)
      }
      // create success result
    }
    // create skipped result
    SummaryStatus(StatusCode.skipped, event)
  }

  // if eid is of type AUDIT, SEARCH, LOG , skip those events
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
    if (summaryStateLong.contains("startTime")) {
      endSession(event, context)
      return
    }
    val ets = event.getOrElse("ets", System.currentTimeMillis()).asInstanceOf[Long]
    val actor = event.get("actor") match {
      case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
      case _ => mutable.Map[String, AnyRef]()
    }
    val cdata = event.get("context") match {
      case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
      case _ => mutable.Map[String, AnyRef]()
    }
    val edata = event.get("edata") match {
      case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
      case _ => mutable.Map[String, AnyRef]()
    }
    val pdata = cdata.get("pdata") match {
      case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
      case _ => mutable.Map[String, AnyRef]()
    }
    summaryStateLong.put("startTime", ets)
    summaryStateLong.put("prevEts", ets)
    summaryStateLong.put("totalTimeSpent", 0)
    summaryStateString.put("did", cdata.getOrElse("did", "").asInstanceOf[String])
    summaryStateString.put("sid", cdata.getOrElse("sid", "").asInstanceOf[String])
    summaryStateString.put("channel", cdata.getOrElse("channel", "").asInstanceOf[String])
    summaryStateString.put("pdataId", pdata.getOrElse("id", "").asInstanceOf[String])
    summaryStateString.put("pdataPid", pdata.getOrElse("pid", "").asInstanceOf[String])
    summaryStateString.put("pdataVer", pdata.getOrElse("ver", "").asInstanceOf[String])
    summaryStateString.put("type", edata.getOrElse("type", "").asInstanceOf[String])
    summaryStateString.put("mode", edata.getOrElse("mode", "").asInstanceOf[String])
    summaryStateString.put("syncts", event.getOrElse("@timestamp", System.currentTimeMillis().toString).asInstanceOf[String])
    summaryStateString.put("uid", actor.getOrElse("id", "").asInstanceOf[String])
    // create timer to break session in case no end received
    val timer: Long = context.timerService().currentProcessingTime() + config.sessionBreakTime*60*1000
    context.timerService().registerProcessingTimeTimer(timer)
  }

  // update the Summary object with captured metrics, like IMPRESSION count, resume an INACTIVE session
  private def updateSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    // if no start present, create
    if (!summaryStateLong.contains("startTime")) {
      startSession(event, context)
    }
    // if start present, add time spent to it
    else {
      val ets = event.getOrElse("ets", System.currentTimeMillis()).asInstanceOf[Long]
      val timeDiff = ets - summaryStateLong.get("prevEts")
      // check for idle time
      if (timeDiff <= config.idleTime * 60 * 1000) {
        val timeSpent = summaryStateLong.get("totalTimeSpent")
        summaryStateLong.put("totalTimeSpent", timeSpent + timeDiff)
      }
      summaryStateLong.put("prevEts", ets)
    }
  }

  // finalize the Summary object on END event, or timer, and return from processing element stream
  private def endSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    val ets = event.getOrElse("ets", System.currentTimeMillis()).asInstanceOf[Long]
    // if start present for key
    if (summaryStateLong.contains("startTime")) {
      // check for idle time
      val timeDiff = ets - summaryStateLong.get("prevEts")
      if (timeDiff <= config.idleTime * 60 * 1000) {
        val timeSpent = summaryStateLong.get("totalTimeSpent")
        summaryStateLong.put("totalTimeSpent", timeSpent + timeDiff)
      }
      summaryStateLong.put("prevEts", ets)
      summaryStateLong.put("endTime", ets)
    }
    // if no start present, skip
    cleanUpState(context)
  }

  // generate the summary event
  private def createSummaryEvent(event: mutable.Map[String, AnyRef]): mutable.Map[String, AnyRef] = {
    mutable.Map(
      "eid" -> "ME_WORKFLOW_SUMMARY",
      "ets" -> Long.box(System.currentTimeMillis()),
      "syncts" -> summaryStateString.get("syncts"),
      "ver" -> "1.0",
      "mid" -> java.util.UUID.randomUUID.toString,
      "uid" -> summaryStateString.get("uid"),
      "context" -> Map(
        "pdata" -> Map(
          "id" -> "AnalyticsDataPipeline",
          "ver" -> "1.0",
          "model" -> "WorkflowSummarizer"
        ),
        "granularity" -> "SESSION",
        "date_range" -> Map(
          "from" -> summaryStateLong.get("startTime"),
          "to" -> summaryStateLong.get("endTime")
        )
      ),
      "dimensions" -> Map(
        "did" -> summaryStateString.get("did"),
        "sid" -> summaryStateString.get("sid"),
        "channel" -> summaryStateString.get("channel"),
        "type" -> summaryStateString.get("type"),
        "mode" -> summaryStateString.get("mode"),
        "pdata" -> Map(
          "id" -> summaryStateString.get("pdataId"),
          "pid" -> summaryStateString.get("pdataPid"),
          "ver" -> summaryStateString.get("pdataVer"),
        ),
      ),
      "edata" -> Map(
        "eks" -> Map(
          "start_time" -> summaryStateLong.get("startTime"),
          "end_time" -> summaryStateLong.get("endTime"),
          "telemetry_version" -> "3.0",
          "time_spent" -> summaryStateLong.get("totalTimeSpent")
        )
      ),
      "tags" -> {
        val channel = summaryStateString.get("channel")
        if (channel != null) List(channel) else List.empty[String]
      },
      "object" -> Map(
        "ver" -> "1.0.0"
      )
    )
  }

  // reset the timer and summary object state
  private def cleanUpState(context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    val timer: Long = timerState.value()
    context.timerService().deleteProcessingTimeTimer(timer)
    timerState.clear()
    summaryStateString.clear()
    summaryStateLong.clear()
  }

}
