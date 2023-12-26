package org.sunbird.obsrv.dataproducts.helper

import com.typesafe.config.Config
import org.joda.time.{DateTime, DateTimeZone}
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.dataproducts.model._

case class BaseMetricHelper(config: Config) {

  val metrics: Map[String, String] = Map(
    "total_dataset_count" -> "total_dataset_count",
    "success_dataset_count" -> "success_dataset_count",
    "failure_dataset_count" -> "failure_dataset_count",
    "total_events_processed" -> "total_events_processed",
    "total_time_taken" -> "total_time_taken"
  )

  private val metricsProducer = KafkaMessageProducer(config)

  private def sync(metric: IJobMetric): Unit = {
    val metricStr = JSONUtil.serialize(metric)
    metricsProducer.sendMessage(message = metricStr)
  }

  def getMetricName(name: String): String = {
    metrics.getOrElse(name, "")
  }

  private def getObject(datasetId: String) = {
    MetricObject(id = datasetId, `type` = "Dataset", ver = "1.0.0")
  }

  def generate(datasetId: String, edata: Edata): Unit = {
    val `object` = getObject(datasetId)
    val actor = Actor(id = "MasterDataProcessorIndexerJob", `type` = "SYSTEM")
    val pdata = Pdata(id = "DataProducts", pid = "MasterDataProcessorIndexerJob", ver = "1.0.0")
    val context = Context(env = config.getString("env"), pdata = pdata)
    val metric = JobMetric(ets = new DateTime(DateTimeZone.UTC).getMillis, actor = actor, context = context, `object` = `object`, edata = edata)
    this.sync(metric)
  }
}
