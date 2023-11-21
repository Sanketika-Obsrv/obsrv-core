package org.sunbird.obsrv.router.functions

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.Constants
import org.sunbird.obsrv.core.streaming.{BaseProcessFunction, Metrics, MetricsList}
import org.sunbird.obsrv.core.util.Util
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.router.task.DruidRouterConfig

import scala.collection.mutable

class DynamicRouterFunction(config: DruidRouterConfig) extends BaseProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[DynamicRouterFunction])

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
  }

  override def close(): Unit = {
    super.close()
  }

  override def getMetricsList(): MetricsList = {
    val metrics = List(config.routerTotalCount, config.routerSuccessCount)
    MetricsList(DatasetRegistry.getDataSetIds(config.datasetType()), metrics)
  }

  override def processElement(msg: mutable.Map[String, AnyRef],
                              ctx: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {

    val datasetId = msg(config.CONST_DATASET).asInstanceOf[String]
    metrics.incCounter(datasetId, config.routerTotalCount)
    val dataset = DatasetRegistry.getDataset(datasetId).get
    val event = Util.getMutableMap(msg(config.CONST_EVENT).asInstanceOf[Map[String, AnyRef]])
    event.put(config.CONST_OBSRV_META, msg(config.CONST_OBSRV_META))
    val routerConfig = dataset.routerConfig
    val topicEventMap = mutable.Map(Constants.TOPIC -> routerConfig.topic, Constants.MESSAGE -> event)
    ctx.output(config.routerOutputTag, topicEventMap)
    metrics.incCounter(datasetId, config.routerSuccessCount)

    msg.remove(config.CONST_EVENT)
    ctx.output(config.statsOutputTag, markComplete(msg, dataset.dataVersion))
  }
}
