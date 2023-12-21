package org.sunbird.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers
import org.sunbird.obsrv.core.util.{PostgresConnect, PostgresConnectionConfig, SystemSettingsService}
import org.sunbird.obsrv.core.model.SystemConfig

class SystemConfigSpec extends BaseSpecWithPostgres with Matchers with MockFactory {
  val configFile: Config = ConfigFactory.load("base-test.conf")
  val postgresConfig: PostgresConnectionConfig = PostgresConnectionConfig(
    configFile.getString("postgres.user"),
    configFile.getString("postgres.password"),
    configFile.getString("postgres.database"),
    configFile.getString("postgres.host"),
    configFile.getInt("postgres.port"),
    configFile.getInt("postgres.maxConnections"))

  override def beforeAll(): Unit = {
    super.beforeAll()
    val postgresConnect = new PostgresConnect(postgresConfig)
    createSystemSettings(postgresConnect)
  }

  override def afterAll(): Unit = {
    val postgresConnect = new PostgresConnect(postgresConfig)
    clearSystemSettings(postgresConnect)
    super.afterAll()
  }

  "SystemConfig" should "populate configurations with values from database" in {
    SystemSettingsService.getSystemSetting("defaultDedupPeriodInSeconds", 604800).intValue() should be(604801)
    SystemConfig.getSystemConfig("maxEventSize", 100L).longValue() should be(1048676)
    SystemConfig.getSystemConfig("defaultDatasetId", "NEW").stringValue() should be("ALL")
    SystemConfig.getSystemConfig("encryptionSecretKey", "test").stringValue() should be("ckW5GFkTtMDNGEr5k67YpQMEBJNX3x2f")
  }

  "SystemConfig" should "throw ObsrvException if key is not found in database" in {
    val thrown = intercept[Exception] {
      SystemSettingsService.getSystemSetting("invalidKey", 604800).intValue()
    }
    thrown.getMessage should be("System setting not found for requested key")
  }
}
