package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.{LogRecordBuilder, Severity}
import org.slf4j.Logger

class OTelLogger(delegate: Logger) {
  private val openTelemetry: OpenTelemetry = OTelService.init("http://0.0.0.0:4317")

  def warn(msg: String): Unit = logWithSeverity(msg, Severity.WARN)(delegate.warn)

  def info(msg: String): Unit = logWithSeverity(msg, Severity.INFO)(delegate.info)

  def error(msg: String): Unit = logWithSeverity(msg, Severity.ERROR)(delegate.error)

  private def logWithSeverity(msg: String, severity: Severity)(logAction: String => Unit): Unit = {
    logAction(msg)
    logToOpenTelemetry(msg, severity)
  }

  private def logToOpenTelemetry(msg: String, severity: Severity): Unit = {
    val customAppenderLogger = openTelemetry.getLogsBridge.get("obsrv-pipeline")
    if (customAppenderLogger != null) {
      val record: LogRecordBuilder = customAppenderLogger.logRecordBuilder()
      record.setSeverity(severity).setBody(msg).emit()
    } else {
      println("Logger bridge not found!")
    }
  }

}
