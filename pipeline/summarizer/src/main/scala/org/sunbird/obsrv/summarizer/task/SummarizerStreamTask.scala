package org.sunbird.obsrv.summarizer.task

import org.sunbird.obsrv.job.util.{FlinkUtil, BaseKeyedStreamTask, FlinkKafkaConnector}

import org.sunbird.obsrv.summarizer.functions.SummarizerFunction

import java.io.File
import scala.collection.mutable
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.datastream.{KeyedStream, SideOutputDataStream}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}

/**
 * Summarization stream task to summarizer batch events if any dependent on dataset configuration
 */
class SummarizerStreamTask(config: SummarizerConfig, kafkaConnector: FlinkKafkaConnector) extends BaseKeyedStreamTask[mutable.Map[String, AnyRef], SummaryKey] {

  private val serialVersionUID = -7729362727131516112L

  // $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
  def process(): Unit = {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    process(env)
    env.execute(config.jobName)
  }
  // $COVERAGE-ON$

  def process(env: StreamExecutionEnvironment): Unit = {
    // extract ets for watermark timestamping
    val timestampAssigner = new SerializableTimestampAssigner[mutable.Map[String, AnyRef]] {
      override def extractTimestamp(event: mutable.Map[String, AnyRef], recordTimestamp: Long): Long = {
        event("ets").asInstanceOf[Long]
      }
    }
    // create watermark with event.ets timestamp
    val watermarkStrategy: WatermarkStrategy[mutable.Map[String, AnyRef]] =
      WatermarkStrategy
        .forBoundedOutOfOrderness(java.time.Duration.ofSeconds(config.waterMarkTimeBound))
        .withTimestampAssigner(timestampAssigner)
    // assign watermark to stream
    val dataStream = getMapDataStream(env, config, kafkaConnector)
      .assignTimestampsAndWatermarks(watermarkStrategy)

    // Create a KeyedStream with explicit KeySelector
    val keyedStream: KeyedStream[mutable.Map[String, AnyRef], SummaryKey] =
      dataStream.keyBy(new SummaryKeySelector())
    // send it to processing
    processStream(keyedStream)
  }

  override def processStream(dataStream: KeyedStream[mutable.Map[String, AnyRef], SummaryKey]): SideOutputDataStream[mutable.Map[String, AnyRef]] = {
    // create stream with Summarizer function as process
    val summarizerStream = dataStream.process(new SummarizerFunction(config))
      .name(config.summarizerFunction).uid(config.summarizerFunction)
      .setParallelism(config.downstreamOperatorsParallelism)
    // Get failed events in side output
    summarizerStream.getSideOutput(config.summarizerFailedEventOutputTag).sinkTo(kafkaConnector.kafkaSink[mutable.Map[String, AnyRef]](config.kafkaFailedTopic))
      .name(config.summarizerFailedEventsProducer).uid(config.summarizerFailedEventsProducer).setParallelism(config.downstreamOperatorsParallelism)
    // get success events in side output
    summarizerStream.getSideOutput(config.successTag()).sinkTo(kafkaConnector.kafkaSink[mutable.Map[String, AnyRef]](config.kafkaSuccessTopic))
      .name(config.summarizerSuccessEventsProducer).uid(config.summarizerSuccessEventsProducer).setParallelism(config.downstreamOperatorsParallelism)
    // add default success stream
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