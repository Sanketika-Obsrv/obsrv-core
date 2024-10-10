package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{Meter, LongCounter}
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.{PeriodicMetricReader}
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes

import scala.collection.mutable
import java.util.concurrent.TimeUnit

object OTelConfiguration {

  def initOpenTelemetry(collectorEndpoint: String): OpenTelemetry = {
    println("Initializing OpenTelemetry...")

    // Configure the OTLP gRPC Metric Exporter
    val otlpMetricExporter: OtlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(30, TimeUnit.SECONDS)
      .build()

    // Configure the OTLP gRPC Span Exporter
    val otlpSpanExporter: OtlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder()
      .setEndpoint(collectorEndpoint)
      .setTimeout(30, TimeUnit.SECONDS)
      .build()

    // Create a service name resource for metrics and traces
    val serviceNameResource: Resource = Resource.create(
      Attributes.of(ResourceAttributes.SERVICE_NAME, "obsrv-pipeline")
    )

    // Configure the TracerProvider with the Span Processor
    val tracerProvider: SdkTracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor.builder(otlpSpanExporter).build())
      .setResource(serviceNameResource)
      .build()

    // Use PeriodicMetricReader to periodically export metrics
    val meterProvider: SdkMeterProvider = SdkMeterProvider.builder()
      .registerMetricReader(PeriodicMetricReader.builder(otlpMetricExporter).build())
      .setResource(serviceNameResource)
      .build()

    // Build the OpenTelemetry SDK with both tracer and meter providers
    val openTelemetry: OpenTelemetrySdk = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .setMeterProvider(meterProvider)
      .build()

    // Ensure proper shutdown of telemetry resources
    sys.addShutdownHook {
      tracerProvider.close()
      meterProvider.shutdown()
    }

    openTelemetry
  }
}