package org.sunbird.obsrv.dataproducts.model

import org.apache.spark.sql.{DataFrame, Dataset}

trait Dispatcher {
  val dispatcherType: String
  val config: Map[String, AnyRef]
  val jobConfig: JobConfig

  def dispatchData(records: DataFrame, config: Map[String, AnyRef]): Unit
}