package org.sunbird.obsrv.core.otel

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.{LogRecordBuilder, Severity}
import org.slf4j.Logger

class OTelLogger(delegate: Logger) {
  private val openTelemetry: OpenTelemetry = OTelService.init("http://0.0.0.0:4317")

  def warn(msg: String, t: Throwable): Unit = logWithSeverity(msg, Severity.WARN, t)(delegate.warn)

  def info(msg: String, t: Throwable): Unit = logWithSeverity(msg, Severity.INFO, t)(delegate.info)

  def error(msg: String, t: Throwable): Unit = logWithSeverity(msg, Severity.ERROR, t)(delegate.error)

  def warn(msg: String): Unit = logWithSeverity(msg, Severity.WARN)(delegate.warn)

  def info(msg: String): Unit = logWithSeverity(msg, Severity.INFO)(delegate.info)

  def error(msg: String): Unit = logWithSeverity(msg, Severity.ERROR)(delegate.error)

  // Enhance further methods if required...


  private def logWithSeverity(msg: String, severity: Severity, t: Throwable)(logAction: (String, Throwable) => Unit): Unit = {
    logAction(msg, t)
    logToOpenTelemetry(msg, severity, Some(t))
  }

  private def logWithSeverity(msg: String, severity: Severity)(logAction: String => Unit): Unit = {
    logAction(msg)
    logToOpenTelemetry(msg, severity, None)
  }

  private def logToOpenTelemetry(msg: String, severity: Severity, throwable: Option[Throwable]): Unit = {
    val customAppenderLogger = openTelemetry.getLogsBridge.get("obsrv-pipeline")
    if (customAppenderLogger != null) {
      val record: LogRecordBuilder = customAppenderLogger.logRecordBuilder()
      record.setSeverity(severity).setBody(msg)

      // Set exception details if available
      throwable.foreach { t =>
        record.setAttribute(AttributeKey.stringKey("exception.message"), t.getMessage)
        record.setAttribute(AttributeKey.stringKey("exception.stacktrace"), t.getStackTrace.mkString("\n"))
      }
      record.emit()
    } else {
      println("Logger bridge not found!")
    }
  }
}
