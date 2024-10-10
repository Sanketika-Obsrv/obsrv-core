//package org.sunbird.obsrv.core.otel
//
//
//import io.opentelemetry.sdk.metrics.{SdkMeterProvider, SdkMeterProviderBuilder}
//import io.opentelemetry.sdk.resources.Resource
//import java.util.List
//import java.util.Set
//
//object SdkMeterProviderConfig {
//  def create(resource: Resource): SdkMeterProvider = {
//    val builder: SdkMeterProviderBuilder = SdkMeterProvider.builder()
//      .setResource(resource)
//      .registerMetricReader(
//        MetricReaderConfig.periodicMetricReader(
//          MetricExporterConfig.otlpHttpMetricExporter("http://localhost:4318/v1/metrics")
//        )
//      )
//
//    // Additional views and configurations can be added here
//    ViewConfig.dropMetricView(builder, "some.custom.metric")
//    ViewConfig.histogramBucketBoundariesView(builder, "http.server.request.duration", List.of(1.0, 5.0, 10.0))
//    ViewConfig.attributeFilterView(builder, "http.client.request.duration", Set.of("http.request.method"))
//
//    builder.build()
//  }
//}
//
