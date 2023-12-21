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

  "SystemSettingsService" should "throw ObsrvException when no default value provided" in {
    val thrown = intercept[Exception] {
      SystemSettingsService.getAllSystemSettings(Map.empty)
    }
    thrown.getMessage should be("Default value not found for requested key")
  }

  "SystemSettingsService" should "return list of System settings with values from database" in {
    val systemSettings = SystemSettingsService.getAllSystemSettings(Map(
      "defaultDedupPeriodInSeconds" -> 604810,
      "maxEventSize" -> 1048671L,
      "defaultDatasetId" -> "new",
      "encryptionSecretKey" -> "test"
    ))
    systemSettings.size should be(4)
    systemSettings.map(f => {
      f.objectKey match {
        case "defaultDedupPeriodInSeconds" => f.intValue() should be(604801)
        case "maxEventSize" => f.longValue() should be(1048676)
        case "defaultDatasetId" => f.stringValue() should be("ALL")
        case "encryptionSecretKey" => f.stringValue() should be("ckW5GFkTtMDNGEr5k67YpQMEBJNX3x2f")
      }
    })
  }

  "SystemSettingsService with empty table" should "return empty list of System settings without throwing error" in {
    val postgresConnect = new PostgresConnect(postgresConfig)
    postgresConnect.execute("TRUNCATE system_settings;")
    val systemSettings = SystemSettingsService.getAllSystemSettings(Map(
      "defaultDedupPeriodInSeconds" -> 604810,
      "maxEventSize" -> 1048671L,
      "defaultDatasetId" -> "new",
"     encryptionSecretKey" -> "test"
    ))
    systemSettings.size should be(0)
  }

  "SystemSettingsService" should "return default value" in {
    val postgresConnect = new PostgresConnect(postgresConfig)
    postgresConnect.execute("TRUNCATE system_settings;")
    // Get all keys and validate default value
    var systemSettings = SystemConfig.getSystemConfig("defaultDedupPeriodInSeconds", 604810)
    systemSettings.intValue() should be(604810)
    systemSettings = SystemConfig.getSystemConfig("maxEventSize", 1048671L)
    systemSettings.longValue() should be(1048671)
    systemSettings = SystemConfig.getSystemConfig("defaultDatasetId", "new")
    systemSettings.stringValue() should be("new")
    systemSettings = SystemConfig.getSystemConfig("encryptionSecretKey", "test")
    systemSettings.stringValue() should be("test")
  }

  "SystemSettingsService" should "throw exception for invalid valueType" in {
    val postgresConnect = new PostgresConnect(postgresConfig)
    postgresConnect.execute("insert into system_settings values('defaultDedupPeriodInSecondsNew', '604801', 'system', 'double', now(),  now(), 'Dedup Period in Seconds');")
    var exception = intercept[Exception] {
      SystemConfig.getSystemConfig("defaultDedupPeriodInSecondsNew", 604810).intValue()
    }
    exception.getMessage should be("Invalid value type for system setting")
    exception = intercept[Exception] {
      SystemConfig.getSystemConfig("defaultDedupPeriodInSecondsNew", 604810).stringValue()
    }
    exception.getMessage should be("Invalid value type for system setting")
    exception = intercept[Exception] {
      SystemConfig.getSystemConfig("defaultDedupPeriodInSecondsNew", 604810).longValue()
    }
    exception.getMessage should be("Invalid value type for system setting")
  }
}
