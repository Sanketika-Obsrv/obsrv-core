package org.sunbird.obsrv.functions

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model._
import org.sunbird.obsrv.core.streaming.Metrics
import org.sunbird.obsrv.model.DatasetModels.Dataset
import org.sunbird.obsrv.streaming.{BaseDatasetProcessFunction, HudiConnectorConfig}

import scala.collection.mutable

class ValidationFunction(config: HudiConnectorConfig)(implicit val eventTypeInfo: TypeInformation[mutable.Map[String, AnyRef]])
  extends BaseDatasetProcessFunction(config) {
  private[this] val logger = LoggerFactory.getLogger(classOf[ValidationFunction])

  override def getMetrics(): List[String] = {
    List()
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
  }

  override def close(): Unit = {
    super.close()
  }

  override def processElement(dataset: Dataset, data: mutable.Map[String, AnyRef],
                              ctx: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {

    val datasetIdOpt = data.get("dataset")
    if (datasetIdOpt.isEmpty) {
      // if dataset is missing in event, log its invalid and move it to failed topic
      println("inside dataset empty check")
      ctx.output(config.invalidEventsOutputTag, markFailed(data, ErrorConstants.MISSING_DATASET_ID, Producer.validator))
    }
    else {
      println("inside dataset success check")
      ctx.output(config.validEventsOutputTag, markSuccess(data, Producer.validator))
    }

  }

}