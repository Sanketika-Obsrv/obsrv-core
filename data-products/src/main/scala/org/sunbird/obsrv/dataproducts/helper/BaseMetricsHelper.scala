package org.sunbird.obsrv.dataproducts.helper

import com.typesafe.config.Config
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.dataproducts.model.{IJobMetric, IMetricsHelper}
import org.sunbird.obsrv.helpers.KafkaMessageProducer

abstract class BaseMetricHelper(config: Config) extends IMetricsHelper {

  override val metrics: Map[String, String] = Map(
    "total_dataset_count" -> "total_dataset_count",
    "success_dataset_count" -> "success_dataset_count",
    "failure_dataset_count" -> "failure_dataset_count",
    "total_events_processed" -> "total_events_processed",
    "total_time_taken" -> "total_time_taken"
  )

  val metricsProducer = KafkaMessageProducer(config)

  def sync(metric: IJobMetric): Unit = {
    val metricStr = JSONUtil.serialize(metric)
    metricsProducer.sendMessage(message = metricStr)
  }
}

