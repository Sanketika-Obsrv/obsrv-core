package org.sunbird.obsrv.writer

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.obsrv.core.streaming.BaseJobConfig

import scala.collection.mutable

class HudiWriterConfig (override val config: Config) extends BaseJobConfig[String](config, "HudiWriterJob") {

  implicit val mapTypeInfo: TypeInformation[mutable.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[mutable.Map[String, AnyRef]])
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

  override def inputTopic(): String = config.getString("kafka.input.topic")

  override def inputConsumer(): String = config.getString("kafka.groupId")

  override def successTag(): OutputTag[String] = OutputTag[String]("dummy-events")

  override def failedEventsOutputTag(): OutputTag[String] = OutputTag[String]("failed-events")
}
