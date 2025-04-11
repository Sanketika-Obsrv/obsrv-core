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
    val summaryStateStringDescriptor: MapStateDescriptor[String, String] = new MapStateDescriptor[String, String]("summaryStateString", classOf[String], classOf[String])
    summaryStateString = getRuntimeContext().getMapState(summaryStateStringDescriptor)
    val summaryStateLongDescriptor: MapStateDescriptor[String, Long] = new MapStateDescriptor[String, Long]("summaryStateLong", classOf[String], classOf[Long])
    summaryStateLong = getRuntimeContext().getMapState(summaryStateLongDescriptor)
    // to store timer state
    val timerDescriptor: ValueStateDescriptor[java.lang.Long] = new ValueStateDescriptor[java.lang.Long]("timer-state", Types.LONG)
    timerState = getRuntimeContext().getState(timerDescriptor)
  }

  override def processElement(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {
    initMetrics("FlinkKafkaConnector", config.jobName)
    metrics.incCounter(config.defaultDatasetID, config.totalEventCount)
    process_event(event, context, metrics)
  }

  // triggered in case no summary state not closed by END event in time
  override def onTimer(timestamp: Long, context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#OnTimerContext, metrics: Metrics): Unit = {
    implicit val event: mutable.Map[String, AnyRef] = mutable.Map(
      "ets" -> Long.box(summaryStateLong.get("prevEts").toLong),
      "eid" -> "END",
      "edata" -> Map("type" -> "app")
    )
    endSession(event, context, metrics)
  }

  // if eid is of type AUDIT, SEARCH, LOG , skip those events
  private def preProcessSummarization(event: mutable.Map[String, AnyRef]): Boolean = {
    val validEventType = List(EventID.AUDIT.toString, EventID.SEARCH.toString, EventID.LOG.toString)
    val eid = event.getOrElse("eid", "").toString
    if (!validEventType.contains(eid)) {
      return true
    }
    false
  }

  // process the telemetry event
  private def process_event(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {
    // check if eid in list
    val preProcessedEvent = preProcessSummarization(event)
    // create mutable result event
    if (preProcessedEvent) {
      val eid = event.getOrElse("eid", "").toString
      val edata = event.get("edata") match {
        case Some(map: Map[_, _]) => mutable.Map() ++ map.asInstanceOf[Map[String, AnyRef]]
        case _ => mutable.Map[String, AnyRef]()
      }
      val eventType = edata.getOrElse("type", "").toString
      eid match {
        case "START" =>
          if (eventType == "app") {
            startSession(event, context, metrics)
          }
          else {
            updateSession(event, context, metrics)
          }
        case "END" =>
          if (eventType == "app") {
            endSession(event, context, metrics)
          }
          else {
            updateSession(event, context, metrics)
          }
        case _ =>
          updateSession(event, context, metrics)
      }
    }
    else {
      metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
    }
  }

  // instantiate a new Summary object on START event
  private def startSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {
    // if another app START received when existing is not closed,
    // check if new START is before previous START
    // update startTime and totalTimeSpent
    val ets = event.getOrElse("ets", System.currentTimeMillis()).asInstanceOf[Long]
    if (summaryStateLong.contains("startTime")) {
      val prevStartTime = summaryStateLong.get("startTime").toLong
      if (ets < prevStartTime) {
        summaryStateLong.put("startTime", ets)
        val timeDiff = prevStartTime - ets
        if (0 < timeDiff && timeDiff <= (config.idleTime * 1000)) {
          val timeSpent = summaryStateLong.get("totalTimeSpent").toLong
          summaryStateLong.put("totalTimeSpent", timeSpent + timeDiff)
          val updateTimeSpent = summaryStateLong.get("totalTimeSpent").toLong
        }
        else {
          metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
        }
        return
      } else {
        endSession(event, context, metrics: Metrics)
      }
    }
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
    // create timer to break session in case no end
    val timer: Long = context.timerService().currentProcessingTime() + (config.sessionBreakTime * 1000)
    summaryStateLong.put("timer", timer)
    context.timerService().registerProcessingTimeTimer(timer)
  }

  // update the Summary object with captured metrics, like IMPRESSION count, resume an INACTIVE session
  private def updateSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {
    // if no start present, create
    if (!summaryStateLong.contains("startTime")) {
      startSession(event, context, metrics)
    }
    // if start present, add time spent to it
    else {
      val ets = event.getOrElse("ets", System.currentTimeMillis()).asInstanceOf[Long]
      val timeDiff = ets - summaryStateLong.get("prevEts").toLong
      // check for idle time
      if (0 < timeDiff && timeDiff <= (config.idleTime * 1000)) {
        val timeSpent = summaryStateLong.get("totalTimeSpent").toLong
        summaryStateLong.put("totalTimeSpent", timeSpent + timeDiff)
        val updateTimeSpent = summaryStateLong.get("totalTimeSpent").toLong
      }
      else {
        metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
      }
      summaryStateLong.put("prevEts", ets)
    }
  }

  // finalize the Summary object on END event, or timer, and return from processing element stream
  private def endSession(event: mutable.Map[String, AnyRef], context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {
    val ets = event.getOrElse("ets", System.currentTimeMillis()).asInstanceOf[Long]
    // if start present for key
    if (summaryStateLong.contains("startTime")) {
      // check for idle time
      val timeDiff = ets - summaryStateLong.get("prevEts")
      if (0 < timeDiff && timeDiff <= (config.idleTime * 1000)) {
        val timeSpent = summaryStateLong.get("totalTimeSpent").toLong
        summaryStateLong.put("totalTimeSpent", timeSpent + timeDiff)
        val updateTimeSpent = summaryStateLong.get("totalTimeSpent").toLong
      }
      else {
        metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
      }
      summaryStateLong.put("prevEts", ets)
      summaryStateLong.put("endTime", ets)
      publishSummary(context, metrics)
    }
    else {
      metrics.incCounter(config.defaultDatasetID, config.skippedSummarizerCount)
    }
  }

  // generate the summary event
  private def createSummaryEvent(): mutable.Map[String, AnyRef] = {
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
      ),
      "obsrv_meta" -> Map(
        "syncts" -> summaryStateString.get("syncts"),
        "processingStartTime" -> System.currentTimeMillis(),
        "flags" -> Map(),
        "timespans" -> Map(),
        "error" -> Map(),
        "source" -> Map(
          "connector" -> "api",
          "connectorInstance" -> "api",
        )
      )
    )
  }

  private def publishSummary(context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context, metrics: Metrics): Unit = {
    val summaryEvent = createSummaryEvent()
    metrics.incCounter(config.defaultDatasetID, config.successSummarizerCount)
    context.output(config.summarizerEventsOutputTag, markSuccess(summaryEvent, Producer.summarizer))
    cleanUpState(context)
  }

  // reset the timer and summary object state
  private def cleanUpState(context: KeyedProcessFunction[SummaryKey, mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context): Unit = {
    val timer = summaryStateLong.get("timer").toLong
    context.timerService().deleteProcessingTimeTimer(timer)
    timerState.clear()
    summaryStateString.clear()
    summaryStateLong.clear()
  }

}
