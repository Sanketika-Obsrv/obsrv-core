package org.sunbird.obsrv.dataproducts.job

import com.typesafe.config.Config
import org.sunbird.obsrv.dataproducts.dispatcher.KafkaDispatcher
import org.sunbird.obsrv.dataproducts.helper.MetricsHelper
import org.sunbird.obsrv.dataproducts.model.{IJobMetric, MasterDataIndexerJobConfig}

case class MasterDataIndexerConfig(config: Config, args: Array[String]) extends MasterDataIndexerJobConfig {

  override val postgresConfig: Map[String, AnyRef] = Map()
}
