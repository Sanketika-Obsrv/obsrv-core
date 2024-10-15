package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.ResourceAttributes

import java.util.concurrent.TimeUnit

object OTelService {

  def init(collectorEndpoint: String): OpenTelemetry = {

    val tracerProvider = createTracerProvider()
    val meterProvider = createMeterProvider(createOtlpMetricExporter(collectorEndpoint))
    val loggerProvider = createLoggerProvider(collectorEndpoint)

    // Build the OpenTelemetry SDK
    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .setMeterProvider(meterProvider)
      .setLoggerProvider(loggerProvider)
      .build()

    // Add a shutdown hook to close SDK and flush logs
    sys.addShutdownHook(openTelemetry.close())
    openTelemetry
  }

  private def createOtlpMetricExporter(endpoint: String): OtlpGrpcMetricExporter = {
    OtlpGrpcMetricExporter.builder()
      .setEndpoint(endpoint)
      .setTimeout(5, TimeUnit.SECONDS)
      .build()
  }

  private def createTracerProvider(): SdkTracerProvider = {
    SdkTracerProvider.builder()
      .setSampler(Sampler.alwaysOn())
      .build()
  }

  private def createMeterProvider(metricExporter: OtlpGrpcMetricExporter): SdkMeterProvider = {
    SdkMeterProvider.builder()
      .registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
      .setResource(createServiceResource("obsrv-pipeline"))
      .build()
  }

  private def createLoggerProvider(endpoint: String): SdkLoggerProvider = {
    SdkLoggerProvider.builder()
      .setResource(createServiceResource("obsrv-pipeline"))
      .addLogRecordProcessor(
        BatchLogRecordProcessor.builder(
          OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(endpoint)
            .build()
        ).build()
      )
      .build()
  }

  // Helper method to create a Resource with service name
  private def createServiceResource(serviceName: String): Resource = {
    Resource.getDefault().toBuilder()
      .put(ResourceAttributes.SERVICE_NAME, serviceName)
      .build()
  }
}
