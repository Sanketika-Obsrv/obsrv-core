package org.sunbird.obsrv.dataproducts.job

import com.typesafe.config.Config
import org.sunbird.obsrv.dataproducts.dispatcher.KafkaDispatcher
import org.sunbird.obsrv.dataproducts.helper.MetricsHelper
import org.sunbird.obsrv.dataproducts.model.{IJobMetric, JobConfig}

case class MasterDataIndexerConfig(config: Config, args: Array[String]) extends JobConfig {

  override val postgresConfig: Map[String, AnyRef] = Map()
  override val name: String = "masterdata-indexer"
}
