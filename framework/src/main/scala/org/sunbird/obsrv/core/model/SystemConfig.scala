package org.sunbird.obsrv.core.model

import org.sunbird.obsrv.core.util.SystemConfigSelector
import org.sunbird.obsrv.core.model.Models.SystemSettings
object SystemConfig {

  private val configurations: List[SystemSettings] = SystemConfigSelector.getSystemConfigurations

  private def getSystemConfig(key: String): Option[SystemSettings] = {
    configurations.find(config => config.objectKey == key)
  }

  val defaultDedupPeriodInSeconds: Int = getSystemConfig("defaultDedupPeriodInSeconds").map(_.intValue()).getOrElse(604800) // 7 days
  val maxEventSize: Long = getSystemConfig("maxEventSize").map(_.longValue()).getOrElse(1048576) // 1 MB
  val defaultDatasetId: String = getSystemConfig("defaultDatasetId").map(_.stringValue()).getOrElse("ALL")

  // secret key length should be 32 characters
  val encryptionSecretKey: String = getSystemConfig("encryptionSecretKey").map(_.stringValue()).getOrElse("ckW5GFkTtMDNGEr5k67YpQMEBJNX3x2f")
}
