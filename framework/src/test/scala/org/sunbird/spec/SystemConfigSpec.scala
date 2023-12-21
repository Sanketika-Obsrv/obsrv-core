package org.sunbird.spec

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
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
    createSchema(postgresConnect)
    insertTestData(postgresConnect)
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  def createSchema(postgresConnect: PostgresConnect): Unit = {
    postgresConnect.execute("CREATE TABLE IF NOT EXISTS system_settings ( key text NOT NULL, value text NOT NULL, category text NOT NULL DEFAULT 'SYSTEM'::text, valuetype text NOT NULL, created_date timestamp NOT NULL DEFAULT now(), updated_date timestamp, label text, PRIMARY KEY (\"key\"));")
  }

  def insertTestData(postgresConnect: PostgresConnect): Unit = {
    postgresConnect.execute("insert into system_settings values('defaultDedupPeriodInSeconds', '604801', 'system', 'int', now(),  now(), 'Dedup Period in Seconds');")
    postgresConnect.execute("insert into system_settings values('maxEventSize', '1048676', 'system', 'long', now(),  now(), 'Max Event Size');")
    postgresConnect.execute("insert into system_settings values('defaultDatasetId', 'DATASETS', 'system', 'string', now(),  now(), 'Default Dataset Id');")
    postgresConnect.execute("insert into system_settings values('encryptionSecretKey', 'akW5GFkTtMDNGEr5k67YpQMEBJNX3x2f', 'system', 'string', now(),  now(), 'Encryption Secret Key');")
  }

  "SystemConfig" should "populate configurations with values from database" in {
    SystemSettingsService.getSystemSetting("defaultDedupPeriodInSeconds", 604800).intValue() should be(604801)
    SystemConfig.getSystemConfig("maxEventSize", 100L).longValue() should be(1048676)
    SystemConfig.getSystemConfig("defaultDatasetId", "ALL").stringValue() should be("DATASETS")
    SystemConfig.getSystemConfig("encryptionSecretKey", "test").stringValue() should be("akW5GFkTtMDNGEr5k67YpQMEBJNX3x2f")
  }
}
