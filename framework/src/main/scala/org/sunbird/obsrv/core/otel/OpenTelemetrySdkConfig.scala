//package org.sunbird.obsrv.core.otel
//
//
//import io.opentelemetry.sdk.OpenTelemetrySdk
//import io.opentelemetry.sdk.resources.Resource
//
//object OpenTelemetrySdkConfig {
//  def create(): OpenTelemetrySdk = {
//    val resource: Resource = ResourceConfig.create() // Create your resource configuration
//    OpenTelemetrySdk.builder()
//      .setTracerProvider(SdkTracerProviderConfig.create(resource)) // Assume you have a tracer provider config
//      .setMeterProvider(SdkMeterProviderConfig.create(resource))
//      .setLoggerProvider(SdkLoggerProviderConfig.create(resource)) // Assume you have a logger provider config
//      .setPropagators(ContextPropagatorsConfig.create()) // Assume you have a context propagator config
//      .build()
//  }
//}
//
//
//
//import io.opentelemetry.sdk.resources.Resource
//import io.opentelemetry.semconv.ServiceAttributes
//
//
//object ResourceConfig {
//  def create(): Resource = Resource.getDefault.toBuilder.put(ServiceAttributes.SERVICE_NAME, "my-service").build
//}
//
