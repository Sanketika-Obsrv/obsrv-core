package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{LongCounter, Meter}
import org.sunbird.obsrv.core.model.Models.SystemEvent

object OTelMetricsGenerator {

  val oTel: OpenTelemetry = OTelService.init()

  def generateOTelSystemEvent(systemEvent: SystemEvent): Unit = {
    val meter: Meter = oTel.meterBuilder("obsrv-pipeline").build()
    val errorCount: LongCounter = meter.counterBuilder("event.error.count")
      .setDescription("Dataset Error Event Count")
      .setUnit("1")
      .build()

    val contextAttributes: Attributes = Attributes.builder()
      .put("ctx.pdata.id", systemEvent.ctx.pdata.id)
      .put("ctx.pdata.type", systemEvent.ctx.pdata.`type`.toString)
      .put("ctx.pdata.pid", systemEvent.ctx.pdata.pid.getOrElse("unknown").toString)
      .put("ctx.module", systemEvent.ctx.module.toString)
      .put("ctx.dataset", systemEvent.ctx.dataset.getOrElse("unknown"))
      .put("ctx.dataset_type", systemEvent.ctx.dataset_type.getOrElse("unknown"))
      .build()

    systemEvent.data.error.foreach { errorLog =>
      // Create attributes from the ErrorLog
      val errorAttributes: Attributes = Attributes.builder()
        .put("error.pdata_id", errorLog.pdata_id.toString)
        .put("error.pdata_status", errorLog.pdata_status.toString)
        .put("error.error_type", errorLog.error_type.toString)
        .put("error.error_code", errorLog.error_code)
        .put("error.error_message", errorLog.error_message)
        .put("error.error_level", errorLog.error_level.toString)
        .put("error.error_count", errorLog.error_count.getOrElse(0).toLong)
        .putAll(contextAttributes)
        .build()

      errorCount.add(1, errorAttributes)
    }

    // Record pipeline stats if present
    systemEvent.data.pipeline_stats.foreach { stats =>
      // Track extractor events count if available
      stats.extractor_events.foreach { events =>
        val extractorEventCounter: LongCounter = meter.counterBuilder("pipeline.extractor.events.count")
          .setDescription("Count of Extractor Events")
          .setUnit("1")
          .build()
        extractorEventCounter.add(events.toLong, contextAttributes)
      }

      stats.extractor_time.foreach { time =>
        val extractorTimeCounter: LongCounter = meter.counterBuilder("pipeline.extractor.time")
          .setDescription("Extractor Processing Time")
          .setUnit("ms")
          .build()

        extractorTimeCounter.add(time, contextAttributes)
      }

      stats.validator_status.foreach { status =>
        val validatorStatusCounter: LongCounter = meter.counterBuilder("pipeline.validator.status")
          .setDescription("Validator Status Count")
          .setUnit("1")
          .build()

        validatorStatusCounter.add(1, Attributes.builder()
          .put("status", status.toString) // Add status attribute
          .putAll(contextAttributes) // Add all context attributes
          .build()
        )
      }

      stats.validator_time.foreach { time =>
        val validatorTimeCounter: LongCounter = meter.counterBuilder("pipeline.validator.time")
          .setDescription("Validator Processing Time")
          .setUnit("ms")
          .build()

        validatorTimeCounter.add(time, contextAttributes)
      }

      // Handle additional metrics similarly for dedup, denorm, and transform if needed
      stats.dedup_time.foreach { time =>
        val dedupTimeCounter: LongCounter = meter.counterBuilder("pipeline.dedup.time")
          .setDescription("Deduplication Processing Time")
          .setUnit("ms")
          .build()

        dedupTimeCounter.add(time, contextAttributes)
      }

      stats.denorm_time.foreach { time =>
        val denormTimeCounter: LongCounter = meter.counterBuilder("pipeline.denorm.time")
          .setDescription("Denormalization Processing Time")
          .setUnit("ms")
          .build()

        denormTimeCounter.add(time, contextAttributes)
      }

      stats.transform_time.foreach { time =>
        val transformTimeCounter: LongCounter = meter.counterBuilder("pipeline.transform.time")
          .setDescription("Transformation Processing Time")
          .setUnit("ms")
          .build()

        transformTimeCounter.add(time, contextAttributes)
      }

      stats.total_processing_time.foreach { time =>
        val totalProcessingTimeCounter: LongCounter = meter.counterBuilder("pipeline.total.processing.time")
          .setDescription("Total Processing Time")
          .setUnit("ms")
          .build()

        totalProcessingTimeCounter.add(time, contextAttributes)
      }

      stats.latency_time.foreach { time =>
        val latencyTimeCounter: LongCounter = meter.counterBuilder("pipeline.latency.time")
          .setDescription("Latency Time")
          .setUnit("ms")
          .build()

        latencyTimeCounter.add(time, contextAttributes)
      }

      stats.processing_time.foreach { time =>
        val processingTimeCounter: LongCounter = meter.counterBuilder("pipeline.processing.time")
          .setDescription("Processing Time")
          .setUnit("ms")
          .build()

        processingTimeCounter.add(time, contextAttributes)
      }
    }

  }
}
