package org.sunbird.spec

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers
import org.sunbird.obsrv.core.util.{PostgresConnect, PostgresConnectionConfig, SystemConfigSelector}
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
    val postgresConnect = new PostgresConnect(postgresConfig)
    val result = SystemConfigSelector.getSystemConfigurations(postgresConfig)
    SystemConfig.getSystemConfig("defaultDedupPeriodInSeconds").map(_.intValue()).get should be(604801)
    SystemConfig.getSystemConfig("maxEventSize").map(_.longValue()).get should be(1048676)
    SystemConfig.getSystemConfig("defaultDatasetId").map(_.stringValue()).get should be("DATASETS")
    SystemConfig.getSystemConfig("encryptionSecretKey").map(_.stringValue()).get should be("akW5GFkTtMDNGEr5k67YpQMEBJNX3x2f")
  }

"SystemConfigSelector" should "return empty list when table does not exist" in {
    val postgresConnect = new PostgresConnect(postgresConfig)
    postgresConnect.execute("DROP TABLE IF EXISTS system_settings;")
    val result = SystemConfigSelector.getSystemConfigurations(postgresConfig)
    result.size should be(0)
    createSchema(postgresConnect)
    insertTestData(postgresConnect)
  }
}
