package org.sunbird.obsrv.streaming

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class HudiConnectorConfigTestSpec extends FlatSpec with Matchers {

  // Regression test: hudi-writer.conf (the actual shipped config) genuinely doesn't define
  // hudi.fs.atomic_creation.support or metrics.reporter.type/host/port - mandatory
  // config.getString(...) calls for these 4 keys used to crash this constructor with
  // ConfigException.Missing on every single startup.
  "HudiConnectorConfig" should "load the shipped hudi-writer.conf without throwing" in {
    val config = ConfigFactory.load("hudi-writer.conf")
    noException should be thrownBy new HudiConnectorConfig(config)
  }

  it should "fall back to safe defaults for the 4 keys missing from hudi-writer.conf" in {
    val config = ConfigFactory.load("hudi-writer.conf")
    val hudiConfig = new HudiConnectorConfig(config)

    // Not "true" - hoodie.fs.atomic_creation.support is a comma-separated filesystem scheme
    // list, not a boolean. Empty default leaves Hudi's own built-in scheme list untouched.
    hudiConfig.hudiFsAtomicCreationSupport should be("")
    hudiConfig.metricsReportType should be("NONE")
    hudiConfig.metricsReporterHost should be("localhost")
    hudiConfig.metricsReporterPort should be("9091")
  }

  it should "use the configured value instead of the default when the key is actually present" in {
    val config = ConfigFactory.parseString(
      """
        |hudi.fs.atomic_creation.support = "s3a,hdfs"
        |metrics.reporter.type = "PROMETHEUS"
        |metrics.reporter.host = "0.0.0.0"
        |metrics.reporter.port = "9249"
        |""".stripMargin
    ).withFallback(ConfigFactory.load("hudi-writer.conf")).resolve()
    val hudiConfig = new HudiConnectorConfig(config)

    hudiConfig.hudiFsAtomicCreationSupport should be("s3a,hdfs")
    hudiConfig.metricsReportType should be("PROMETHEUS")
    hudiConfig.metricsReporterHost should be("0.0.0.0")
    hudiConfig.metricsReporterPort should be("9249")
  }

}
