package org.sunbird.obsrv.core.util

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.core.model.Models.SystemSettings

import java.io.File
import java.sql.ResultSet

object SystemSettingsService {
  private[this] val logger = LoggerFactory.getLogger(SystemSettingsService.getClass)
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

  @throws[Exception]
  def getSystemSetting(key: String, defaultValue: Any, postgresConfig: PostgresConnectionConfig = postgresConfiguration): SystemSettings = {
    val postgresConnect = new PostgresConnect(postgresConfig)
    val rs = postgresConnect.executeQuery(s"SELECT * FROM system_settings WHERE key = '${key}'")
    val result = Iterator.continually((rs, rs.next)).takeWhile(f => f._2).map(f => f._1).map(result => {
      val systemSettings = parseSystemSettings(result, defaultValue)
      systemSettings
    }).toList
    postgresConnect.closeConnection()
    if(result.isEmpty) {
      throw new ObsrvException(ErrorConstants.SYSTEM_SETTING_NOT_FOUND)
    }
    result.head
  }

  private def parseSystemSettings(rs: ResultSet, defaultValue: Any): SystemSettings = {
    val key = rs.getString("key")
    val value = rs.getString("value")
    val category = rs.getString("category")
    val valueType = rs.getString("valuetype")
    val label = rs.getString("label")

    new SystemSettings(key, value, category, valueType, label)(defaultValue)
  }
}
