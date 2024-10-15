package org.sunbird.obsrv.core.otel

import org.slf4j.{Logger, LoggerFactory}
import io.opentelemetry.api.logs.{Severity, LogRecordBuilder}
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender

class OpenTelemetryLogger(delegate: Logger, openTelemetry: OpenTelemetry) {
  // Initialize OpenTelemetry
 // private val openTelemetry: OpenTelemetry = OTelConfiguration.initOpenTelemetry("http://0.0.0.0:4317")


  // Log a warning message
  def warn(msg: String): Unit = logWithSeverity(msg, Severity.WARN)(delegate.warn)

  // Log an informational message
  def info(msg: String): Unit = logWithSeverity(msg, Severity.INFO)(delegate.info)

  // Log an error message
  def error(msg: String): Unit = logWithSeverity(msg, Severity.ERROR)(delegate.error)

  private def logWithSeverity(msg: String, severity: Severity)(logAction: String => Unit): Unit = {
   // OpenTelemetryAppender.install(openTelemetry)
    logAction(msg) // Delegate to SLF4J logger
    logToOpenTelemetry(msg, severity) // Log to OpenTelemetry
  }

  private def logToOpenTelemetry(msg: String, severity: Severity): Unit = {
    val customAppenderLogger = openTelemetry.getLogsBridge().get("obsrv-pipeline-logger")
    val record: LogRecordBuilder = customAppenderLogger.logRecordBuilder()
    record.setSeverity(severity).setBody(msg).emit()
  }
}
