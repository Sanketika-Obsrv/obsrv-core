package org.sunbird.obsrv.summarizer.task

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.sunbird.obsrv.core.streaming.{BaseStreamTask, FlinkKafkaConnector}
import org.sunbird.obsrv.core.util.FlinkUtil
import org.sunbird.obsrv.summarizer.functions.SummarizerFunction

import java.io.File
import scala.collection.mutable

/**
 * Summarization stream task to summarizet batch events if any dependent on dataset configuration
 */
class SummarizerStreamTask(config: SummarizerConfig, kafkaConnector: FlinkKafkaConnector) extends BaseStreamTask[mutable.Map[String, AnyRef]] {

  private val serialVersionUID = -7729362727131516112L

  // $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
  def process(): Unit = {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    process(env)
    env.execute(config.jobName)
  }
  // $COVERAGE-ON$

  def process(env: StreamExecutionEnvironment): Unit = {

    val watermarkStrategy: WatermarkStrategy[mutable.Map[String, AnyRef]] = WatermarkStrategy
      .forBoundedOutOfOrderness[mutable.Map[String, AnyRef]](Duration.ofSeconds(5))
      .withTimestampAssigner(new TimestampAssigner[mutable.Map[String, AnyRef]] {
        override def extractTimestamp(event: mutable.Map[String, AnyRef], recordTimestamp: Long): Long = {
          event.ets
        }
      })
      .withIdleness(Duration.ofMinutes(1))

    val dataStream = getMapDataStream(env, config, kafkaConnector)
      .keyBy(event => event.did)
      .assignTimestampsAndWatermarks(watermarkStrategy)

    processStream(dataStream)
  }

  override def processStream(dataStream: DataStream[mutable.Map[String, AnyRef]]): DataStream[mutable.Map[String, AnyRef]] = {

    val summarizerStream = dataStream.process(new SummarizerFunction(config))
      .name(config.summarizerFunction).uid(config.summarizerFunction)
      .setParallelism(config.downstreamOperatorsParallelism)

    summarizerStream.getSideOutput(config.summarizerFailedEventOutputTag).sinkTo(kafkaConnector.kafkaSink[mutable.Map[String, AnyRef]](config.kafkaFailedTopic))
      .name(config.summarizerFailedEventsProducer).uid(config.summarizerFailedEventsProducer).setParallelism(config.downstreamOperatorsParallelism)

    summarizerStream.getSideOutput(config.successTag()).sinkTo(kafkaConnector.kafkaSink[mutable.Map[String, AnyRef]](config.kafkaSuccessTopic))
      .name(config.summarizerSuccessEventsProducer).uid(config.summarizerSuccessEventsProducer).setParallelism(config.downstreamOperatorsParallelism)

    addDefaultSinks(summarizerStream, config, kafkaConnector)
    summarizerStream.getSideOutput(config.successTag())
  }
}

// $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
object SummarizerStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("summarizer.conf").withFallback(ConfigFactory.systemEnvironment()))
    val summarizerConfig = new SummarizerConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(summarizerConfig)
    val task = new SummarizerStreamTask(summarizerConfig, kafkaUtil)
    task.process()
  }
}
// $COVERAGE-ON$