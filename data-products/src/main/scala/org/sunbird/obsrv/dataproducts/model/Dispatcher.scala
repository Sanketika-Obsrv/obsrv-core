package org.sunbird.obsrv.dataproducts.model

import org.apache.spark.sql.{DataFrame, Dataset}

trait Dispatcher {
  val dispatcherType: String
  val config: Map[String, AnyRef]
  val jobConfig: JobConfig

  def dispatch[T](records: Dataset[T], config: Map[String, AnyRef]): Unit

  def dispatchData(records: DataFrame, config: Map[String, AnyRef]): Unit
}