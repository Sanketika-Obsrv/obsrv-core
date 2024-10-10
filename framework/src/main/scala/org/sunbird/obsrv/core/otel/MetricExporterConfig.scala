package org.sunbird.obsrv.core.otel

import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter

import java.time.Duration


object MetricExporterConfig {
  def otlpHttpMetricExporter(endpoint: String): MetricExporter = OtlpHttpMetricExporter.builder.setEndpoint(endpoint).addHeader("api-key", "value").setTimeout(Duration.ofSeconds(10)).build

  def otlpGrpcMetricExporter(endpoint: String): MetricExporter = OtlpGrpcMetricExporter.builder.setEndpoint(endpoint).addHeader("api-key", "value").setTimeout(Duration.ofSeconds(10)).build

  def logginMetricExporter: MetricExporter = LoggingMetricExporter.create
}