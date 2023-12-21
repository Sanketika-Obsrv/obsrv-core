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
  def getAllSystemSettings(defaultValues: Map[String, Any], postgresConfig: PostgresConnectionConfig = postgresConfiguration): List[SystemSettings] = {
    val postgresConnect = new PostgresConnect(postgresConfig)
    val rs = postgresConnect.executeQuery("SELECT * FROM system_settings")
    val result = Iterator.continually((rs, rs.next)).takeWhile(f => f._2).map(f => f._1).map(result => {
      val key = rs.getString("key")
      parseSystemSettings(result, defaultValues.getOrElse(key, throw new ObsrvException(ErrorConstants.SYSTEM_SETTING_DEFAULT_VALUE_NOT_FOUND)))
    }).toList
    postgresConnect.closeConnection()
    result
  }

  @throws[Exception]
  def getSystemSetting(key: String, defaultValue: Any, postgresConfig: PostgresConnectionConfig = postgresConfiguration): SystemSettings = {
    val postgresConnect = new PostgresConnect(postgresConfig)
    val rs = postgresConnect.executeQuery(s"SELECT * FROM system_settings WHERE key = '${key}'")
    var result = Iterator.continually((rs, rs.next)).takeWhile(f => f._2).map(f => f._1).map(result => {
      parseSystemSettings(result, defaultValue)
    }).toList
    postgresConnect.closeConnection()
    if(result.isEmpty) {
      result = List(new SystemSettings()(defaultValue))
    }
    result.head
  }

  private def parseSystemSettings(rs: ResultSet, defaultValue: Any): SystemSettings = {
    val key = Option(rs.getString("key"))
    val value = Option(rs.getString("value"))
    val category = Option(rs.getString("category"))
    val valueType = Option(rs.getString("valuetype"))
    val label = Option(rs.getString("label"))

    new SystemSettings(key, value, category, valueType, label)(defaultValue)
  }
}
