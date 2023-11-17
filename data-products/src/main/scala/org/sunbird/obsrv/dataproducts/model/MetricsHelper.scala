package org.sunbird.obsrv.dataproducts.model

import org.sunbird.obsrv.helpers.KafkaMessageProducer


trait IMetricsHelper {
  val metricsProducer: KafkaMessageProducer
  val metrics: Map[String, String]
  def sync(metric: IJobMetric): Unit

  def generate(datasetId: String, edata: Edata): Unit
  def getMetricName(name: String) = {
    metrics.get(name).getOrElse("")
  }
}
