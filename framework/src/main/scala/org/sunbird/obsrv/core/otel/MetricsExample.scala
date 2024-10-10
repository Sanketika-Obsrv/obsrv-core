import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.LongCounter
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.util.JSONUtil

object MetricsExample {

  private val logger = LoggerFactory.getLogger(getClass)

  def recordHttpRequest(openTelemetry: OpenTelemetry): Unit = {
    // Get the meter from OpenTelemetry
    val meter: Meter = openTelemetry.getMeter("obsrv-pipeline")

    // Create or get the request counter metric
    val requestCounter: LongCounter = meter.counterBuilder("http.server.requests.count")
      .setDescription("Count of HTTP server requests")
      .setUnit("1")
      .build()

    // Increment the request counter
    requestCounter.add(1)

    // Prepare the metric event for logging
    val timestamp = System.currentTimeMillis() // Current timestamp in milliseconds
    val metricEvent = Map(
      "resource" -> Map(
        "service.name" -> "obsrv-pipeline",
        "service.version" -> "1.0.0"
      ),
      "metrics" -> Seq(
        Map(
          "name" -> "http.server.requests.count",
          "description" -> "Count of HTTP server requests",
          "unit" -> "1",
          "dataPoints" -> Seq(
            Map(
              "timestamp" -> timestamp,
              "value" -> requestCounter.add(1), // Get the current count value
              "attributes" -> Map(
                "http.method" -> "GET", // Example method
                "http.status_code" -> 200 // Example status code
              )
            )
          )
        )
      )
    )

    // Log the event in JSON format
    logger.info(JSONUtil.serialize(metricEvent))
  }
}


