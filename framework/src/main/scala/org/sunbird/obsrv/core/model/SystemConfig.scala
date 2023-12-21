package org.sunbird.obsrv.core.model

import org.sunbird.obsrv.core.util.SystemSettingsService
import org.sunbird.obsrv.core.model.Models.SystemSettings
object SystemConfig {
  def getSystemConfig(key: String, defaultValue: Any): SystemSettings = {
    SystemSettingsService.getSystemSetting(key, defaultValue)
  }

}
