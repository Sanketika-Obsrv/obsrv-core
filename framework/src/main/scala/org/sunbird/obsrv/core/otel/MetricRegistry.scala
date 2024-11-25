package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.{LongCounter, Meter}

object MetricRegistry {
  private val oTel: OpenTelemetry = OTelService.init()
  private val meter: Meter = oTel.meterBuilder("obsrv-pipeline").build()

  val errorCount: LongCounter = meter.counterBuilder("event.error.count")
    .setDescription("Dataset Error Event Count")
    .setUnit("1")
    .build()

  val processingTimeCounter: LongCounter = meter.counterBuilder("pipeline.processing.time")
    .setDescription("Processing Time")
    .setUnit("ms")
    .build()

  val totalProcessingTimeCounter: LongCounter = meter.counterBuilder("pipeline.total.processing.time")
    .setDescription("Total Processing Time")
    .setUnit("ms")
    .build()

  val latencyTimeCounter: LongCounter = meter.counterBuilder("pipeline.latency.time")
    .setDescription("Latency Time")
    .setUnit("ms")
    .build()

  val extractorEventCounter: LongCounter = meter.counterBuilder("pipeline.extractor.events.count")
    .setDescription("Count of Extractor Events")
    .setUnit("1")
    .build()

  val extractorTimeCounter: LongCounter = meter.counterBuilder("pipeline.extractor.time")
    .setDescription("Extractor Processing Time")
    .setUnit("ms")
    .build()

  val transformStatusCounter: LongCounter = meter.counterBuilder("pipeline.transform.status")
    .setDescription("Data transform Status")
    .setUnit("1")
    .build()

  val transformTimeCounter: LongCounter = meter.counterBuilder("pipeline.transform.time")
    .setDescription("Transformation Processing Time")
    .setUnit("ms")
    .build()

  val denormStatusCounter: LongCounter = meter.counterBuilder("pipeline.denorm.status")
    .setDescription("Denorm Status")
    .setUnit("1")
    .build()

  val denormTimeCounter: LongCounter = meter.counterBuilder("pipeline.denorm.time")
    .setDescription("Denormalization Processing Time")
    .setUnit("ms")
    .build()

  val dedupStatusCounter: LongCounter = meter.counterBuilder("pipeline.de-dup.status")
    .setDescription("De-dup Status")
    .setUnit("1")
    .build()

  val dedupTimeCounter: LongCounter = meter.counterBuilder("pipeline.dedup.time")
    .setDescription("Deduplication Processing Time")
    .setUnit("ms")
    .build()

  val validatorTimeCounter: LongCounter = meter.counterBuilder("pipeline.validator.time")
    .setDescription("Validator Processing Time")
    .setUnit("ms")
    .build()

  val validatorStatusCounter: LongCounter = meter.counterBuilder("pipeline.validator.status")
    .setDescription("Validator Status")
    .setUnit("1")
    .build()

  val extractorStatusCounter: LongCounter = meter.counterBuilder("pipeline.extractor.status")
    .setDescription("Extractor Status")
    .setUnit("1")
    .build()

}

