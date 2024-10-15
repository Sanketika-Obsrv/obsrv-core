package org.sunbird.obsrv.core.otel

import org.slf4j.LoggerFactory

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.{LongCounter, Meter}
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender
import org.sunbird.obsrv.core.otel.OTelConfiguration

class Example {

}

object OTelExecutor {


  def main(args: Array[String]): Unit = {
    println("Starting OpenTelemetry Metrics Generation...")
    // Initialize OpenTelemetry
    val openTelemetry: OpenTelemetry = OTelConfiguration.initOpenTelemetry("http://0.0.0.0:4317")
    val slf4jLogger: OpenTelemetryLogger = new OpenTelemetryLogger(LoggerFactory.getLogger(classOf[Example]), openTelemetry)

    slf4jLogger.warn(s"Denormalizer | Denorm operation is not successful | dataset= | denormStatus=true")

    OpenTelemetryAppender.install(openTelemetry)

    // Create a meter for the pipeline
    val meter: Meter = openTelemetry.meterBuilder("obsrv-pipeline").build()

    // Create a counter metric for HTTP requests
    val requestCounter: LongCounter = meter.counterBuilder("http.server.requests.count")
      .setDescription("Count of HTTP server requests")
      .setUnit("1")
      .build()

    // Example increment of the counter
    requestCounter.add(1, Attributes.builder().put("http.method", "GET").build())
    println("Counter incremented for HTTP GET request.")
  }
}
