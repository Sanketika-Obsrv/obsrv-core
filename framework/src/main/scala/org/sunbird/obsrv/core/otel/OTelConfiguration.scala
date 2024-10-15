package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.logs.{Logger, LoggerProvider, Severity}
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import java.util.logging.LogManager

object OTelConfiguration {

  private var logger: Logger = _

//  private val slf4jLogger = LoggerFactory.getLogger("slf4j-logger")



  def initOpenTelemetry(collectorEndpoint: String): OpenTelemetry = {
    println("Initializing OpenTelemetry...")


    // Configure the OTLP gRPC Metric Exporter
    val otlpMetricExporter = OtlpGrpcMetricExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(30, TimeUnit.SECONDS)
      .build()

    // Configure the OTLP gRPC Span Exporter
    val otlpSpanExporter = OtlpGrpcSpanExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(30, TimeUnit.SECONDS)
      .build()

    // Configure the OTLP gRPC Log Exporter
    val otlpLogExporter = OtlpGrpcLogRecordExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(30, TimeUnit.SECONDS)
      .build()

    // Create a service name resource for metrics and traces
    val serviceNameResource = Resource.create(
      Attributes.of(ResourceAttributes.SERVICE_NAME, "obsrv-pipeline")
    )

    // Configure the TracerProvider with the Span Processor
    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor.builder(otlpSpanExporter).build())
      .setResource(serviceNameResource)
      .build()

    // Use PeriodicMetricReader to periodically export metrics
    val meterProvider = SdkMeterProvider.builder()
      .registerMetricReader(PeriodicMetricReader.builder(otlpMetricExporter).build())
      .setResource(serviceNameResource)
      .build()

    // Initialize LoggerProvider
    val loggerProvider = SdkLoggerProvider.builder()
      .addLogRecordProcessor(BatchLogRecordProcessor.builder(otlpLogExporter).build())
      .setResource(serviceNameResource)
      .build()

    // Initialize the OpenTelemetry instance
    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .setMeterProvider(meterProvider)
      .setLoggerProvider(loggerProvider)
      .build()

    // Ensure proper shutdown of telemetry resources
    sys.addShutdownHook {
      tracerProvider.close()
      meterProvider.shutdown()
      loggerProvider.shutdown() // Properly shutdown the logger provider
    }

    // Use the OpenTelemetry Logs Bridge to get the logger
    logger = GlobalOpenTelemetry.get().getLogsBridge().get("obsrv-pipeline-logger")

    // Log an example message
    logger.logRecordBuilder()
      .setBody("OpenTelemetry initialized successfully.")
      .setSeverity(Severity.INFO)
      .emit()

    openTelemetry
  }

  def logMessage(message: String): Unit = {
    logger.logRecordBuilder()
      .setBody(message)
      .setSeverity(Severity.INFO)
      .setAttribute(AttributeKey.stringKey("job"), "custom")
      .emit()
  }
}
