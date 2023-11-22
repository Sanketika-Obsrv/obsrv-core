package org.sunbird.obsrv.transformer.functions

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.streaming.Metrics
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.registry.DatasetRegistry
import org.sunbird.obsrv.streaming.BaseDatasetProcessFunction
import org.sunbird.obsrv.transformer.task.TransformerConfig

import scala.collection.mutable

class TransformerFunction(config: TransformerConfig)(implicit val eventTypeInfo: TypeInformation[mutable.Map[String, AnyRef]])
  extends BaseDatasetProcessFunction(config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[TransformerFunction])

  override def getMetrics(): List[String] = {
    List(config.totalEventCount, config.transformSuccessCount, config.transformFailedCount, config.transformSkippedCount)
  }


  /**
   * Method to process the event transformations
   */
  override def processElement(dataset: Dataset, msg: mutable.Map[String, AnyRef],
                              context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {
    try {
      metrics.incCounter(dataset.id, config.totalEventCount)
      val datasetTransformations = DatasetRegistry.getDatasetTransformations(dataset.id)
      if (datasetTransformations.isDefined) {
        // TODO: Perform transformations
        metrics.incCounter(dataset.id, config.transformSuccessCount)
        context.output(config.transformerOutputTag, markSuccess(msg, config.jobName))
      } else {
        metrics.incCounter(dataset.id, config.transformSkippedCount)
        context.output(config.transformerOutputTag, markSkipped(msg, config.jobName))
      }
    } catch {
      case ex: Exception =>
        logger.error("TransformerFunction:processElement()-Exception: ", ex.getMessage)
        ex.printStackTrace()
    }

  }

}