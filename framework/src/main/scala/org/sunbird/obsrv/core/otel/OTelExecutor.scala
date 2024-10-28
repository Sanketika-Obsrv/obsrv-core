package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{LongCounter, Meter}
import org.slf4j.LoggerFactory

class Example {

}

object OTelExecutor {


  def main(args: Array[String]): Unit = {
    println("Starting OpenTelemetry Metrics Generation...")
    val openTelemetry = OTelService.init()
    val meter: Meter = openTelemetry.meterBuilder("obsrv-pipeline").build()
    val requestCounter: LongCounter = meter.counterBuilder("http.server.requests.count")
      .setDescription("Count of HTTP server requests")
      .setUnit("1")
      .build()
    requestCounter.add(1, Attributes.builder().put("http.method", "GET").build())
  }
}
