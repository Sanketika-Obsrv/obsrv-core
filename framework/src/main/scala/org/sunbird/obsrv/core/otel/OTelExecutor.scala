package org.sunbird.obsrv.core.otel

import com.sun.org.slf4j.internal.LoggerFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.{LongCounter, Meter}
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender
import org.slf4j.LoggerFactory

import scala.collection.mutable
import org.sunbird.obsrv.core.model.Models.ErrorLog
import org.sunbird.obsrv.core.model.Producer.Producer
import org.sunbird.obsrv.core.streaming.BaseJobConfig
import org.sunbird.obsrv.core.util.JSONUtil
import org._

import java.util.logging.LogManager

object OTelExecutor {
  def main(args: Array[String]): Unit = {
    println("Starting OpenTelemetry Metrics Generation...")


    //val log4jLogger = LogManager.getLogManager.getLogger("log4j-logger")
    //lazy val slf4jLogger = LoggerFactory.getILoggerFactory.getLogger("slf4j-logger")

    // Initialize OpenTelemetry
    val openTelemetry: OpenTelemetry = OTelConfiguration.initOpenTelemetry("http://0.0.0.0:4317")

    //OpenTelemetryAppender.install(openTelemetry)


    val meter: Meter = openTelemetry.meterBuilder("obsrv-pipeline").build()
    OTelConfiguration.logMessage("Hello, This is example data")


     //Create a counter metric for HTTP requests
    val requestCounter: LongCounter = meter.counterBuilder("http.server.requests.count")
      .setDescription("Count of HTTP server requests")
      .setUnit("1")
      .build()

    // Generate a system event with default values
    val metricEvent = createDefaultMetricEvent()

    // Extract attributes from the generated metric event
    val attributes: Attributes = Attributes.builder()
      .put("error.pdata_id", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("pdata_id").toString)
      .put("error.pdata_status", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("pdata_status").toString)
      .put("error.error_type", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("error_type").toString)
      .put("error.error_code", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("error_code").toString)
      .put("error.error_message", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("error_message").toString)
      .put("error.error_level", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("error_level").toString)
      .put("error.error_count", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("error").asInstanceOf[mutable.Map[String, AnyRef]]("error_count").asInstanceOf[String].toInt.toLong) // Convert String to Long
      .put("pipeline.validator_time", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("pipeline_stats").asInstanceOf[mutable.Map[String, AnyRef]]("validator_time").asInstanceOf[String].toInt.toLong) // Convert String to Long
      //.put("pipeline.validator_time", metricEvent("data").asInstanceOf[mutable.Map[String, AnyRef]]("pipeline_stats").asInstanceOf[mutable.Map[String, AnyRef]]("validator_time").asInstanceOf[Int].toLong) // Convert to Long
      .put("dataset", metricEvent("ctx").asInstanceOf[mutable.Map[String, AnyRef]]("dataset").toString)
      .build()

    // Increment the counter by 1 with the extracted attributes
    requestCounter.add(1, attributes)
  }

  def createDefaultMetricEvent(): mutable.Map[String, AnyRef] = {
    mutable.Map[String, AnyRef](
      "etype" -> "METRIC",
      "ctx" -> mutable.Map[String, AnyRef](
        "module" -> "processing",
        "pdata" -> mutable.Map[String, AnyRef](
          "id" -> "PipelinePreprocessorJob",
          "type" -> "flink",
          "pid" -> "validator"
        ),
        "dataset" -> "dX"
      ),
      "data" -> mutable.Map[String, AnyRef](
        "error" -> mutable.Map[String, AnyRef](
          "pdata_id" -> "validator",
          "pdata_status" -> "failed",
          "error_type" -> "MissingDatasetId",
          "error_code" -> "ERR_EXT_1005",
          "error_message" -> "Dataset configuration is missing",
          "error_level" -> "critical",
          "error_count" -> "1"
        ),
        "pipeline_stats" -> mutable.Map[String, AnyRef](
          "validator_status" -> "failed",
          "validator_time" -> "759"
        )
      ),
      "ets" -> "1728543248485L"
    )
  }
}
