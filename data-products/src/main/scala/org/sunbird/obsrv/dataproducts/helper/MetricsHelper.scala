package org.sunbird.obsrv.dataproducts.helper

import org.sunbird.obsrv.dataproducts.model.{Actor, Context, Edata, JobConfig, JobMetric, MetricObject, Pdata}

case class MetricsHelper(jobConfig: JobConfig) extends BaseMetricHelper(jobConfig.config) {

  private def getObject(datasetId: String) = {
    MetricObject(id = datasetId, `type` = "Dataset", ver = "1.0.0")
  }

  def generate(datasetId: String, edata: Edata) = {
    val `object` = getObject(datasetId)
    val actor = Actor(id = "MasterDataProcessorIndexerJob", `type` = "SYSTEM")
    val pdata = Pdata(id = "DataProducts", pid = "MasterDataProcessorIndexerJob", ver = "1.0.0")
    val context = Context(env = jobConfig.config.getString("env"), pdata = pdata)
    val metric = JobMetric(actor = actor, context = context, `object` = `object`, edata = edata)
    this.sync(metric)
  }
}

