package org.sunbird.obsrv.dataproducts.model

import com.typesafe.config.Config

trait JobConfig {
  val name: String;
  val config: Config;
  val args: Array[String]
  val postgresConfig: Map[String, AnyRef]
}
