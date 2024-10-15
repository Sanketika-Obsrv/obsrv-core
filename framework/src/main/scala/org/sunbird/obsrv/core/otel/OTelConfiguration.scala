package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes

import java.util.concurrent.TimeUnit

object OTelConfiguration {

  def initOpenTelemetry(collectorEndpoint: String): OpenTelemetry = {
    println("Initializing OpenTelemetry...")

    // Configure the OTLP gRPC Metric Exporter
    val otlpMetricExporter = OtlpGrpcMetricExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(5, TimeUnit.SECONDS)
      .build()

    // Configure the OTLP gRPC Span Exporter
    val otlpSpanExporter = OtlpGrpcSpanExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(5, TimeUnit.SECONDS)
      .build()

    // Configure the OTLP gRPC Log Exporter
    val otlpLogExporter = OtlpGrpcLogRecordExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(5, TimeUnit.SECONDS)
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

    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .setMeterProvider(meterProvider)
      .setLoggerProvider(loggerProvider)
      .build()

    sys.addShutdownHook {
      tracerProvider.close()
      meterProvider.shutdown()
      loggerProvider.shutdown()
    }
    openTelemetry
  }

}
