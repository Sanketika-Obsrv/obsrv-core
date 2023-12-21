package org.sunbird.obsrv.core.util

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.Models.SystemSettings

import java.io.File
import java.sql.ResultSet

object SystemConfigSelector {
  private[this] val logger = LoggerFactory.getLogger(SystemConfigSelector.getClass)
  private val configFile = new File("/data/flink/conf/baseconfig.conf")
  // $COVERAGE-OFF$
  val config: Config = if (configFile.exists()) {
    println("Loading configuration file cluster baseconfig.conf...")
    ConfigFactory.parseFile(configFile).resolve()
  } else {
    // $COVERAGE-ON$
    println("Loading configuration file baseconfig.conf inside the jar...")
    ConfigFactory.load("baseconfig.conf").withFallback(ConfigFactory.systemEnvironment())
  }
  private val postgresConfiguration = PostgresConnectionConfig(
    config.getString("postgres.user"),
    config.getString("postgres.password"),
    config.getString("postgres.database"),
    config.getString("postgres.host"),
    config.getInt("postgres.port"),
    config.getInt("postgres.maxConnections"))

  def getSystemConfigurations(postgresConfig: PostgresConnectionConfig = postgresConfiguration): List[SystemSettings] = {
    try {
      val postgresConnect = new PostgresConnect(postgresConfig)
      val rs = postgresConnect.executeQuery("SELECT * FROM system_settings")
      val result = Iterator.continually((rs, rs.next)).takeWhile(f => f._2).map(f => f._1).map(result => {
        val systemSettings = parseSystemSettings(result)
        systemSettings
      }).toList
      postgresConnect.closeConnection()
      result
    } catch {
      case ex: Exception =>
        logger.error("Exception while reading system settings from Postgres", ex)
        List()
    }
  }

  private def parseSystemSettings(rs: ResultSet): SystemSettings = {
    val key = rs.getString("key")
    val value = rs.getString("value")
    val category = rs.getString("category")
    val valueType = rs.getString("valuetype")
    val label = rs.getString("label")

    new SystemSettings(key, value, category, valueType, label)
  }
}