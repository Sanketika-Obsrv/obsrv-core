package org.sunbird.obsrv.denormalizer.task

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.datastream.WindowedStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.sunbird.obsrv.core.streaming.FlinkKafkaConnector
import org.sunbird.obsrv.core.util.{DatasetKeySelector, FlinkUtil, TumblingProcessingTimeCountWindows}
import org.sunbird.obsrv.denormalizer.functions.DenormalizerWindowFunction

import java.io.File
import scala.collection.mutable

/**
 * Denormalization stream task does the following pipeline processing in a sequence:
 */
class DenormalizerWindowStreamTask(config: DenormalizerConfig, kafkaConnector: FlinkKafkaConnector) {

  private val serialVersionUID = -7729362727131516112L

  def process(): Unit = {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    implicit val eventTypeInfo: TypeInformation[mutable.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[mutable.Map[String, AnyRef]])

    val source = kafkaConnector.kafkaMapSource(config.inputTopic())
    val windowedStream: WindowedStream[mutable.Map[String, AnyRef], String, TimeWindow] = env.fromSource(source, WatermarkStrategy.noWatermarks[mutable.Map[String, AnyRef]](), config.denormalizationConsumer).uid(config.denormalizationConsumer)
      .setParallelism(config.kafkaConsumerParallelism).rebalance()
      .keyBy(new DatasetKeySelector())
      .window(TumblingProcessingTimeCountWindows.of(Time.seconds(config.windowTime), config.windowCount))

    val denormStream = windowedStream
        .process(new DenormalizerWindowFunction(config)).name(config.denormalizationFunction).uid(config.denormalizationFunction)
        .setParallelism(config.downstreamOperatorsParallelism)

    denormStream.getSideOutput(config.denormEventsTag).sinkTo(kafkaConnector.kafkaMapSink(config.denormOutputTopic))
      .name(config.DENORM_EVENTS_PRODUCER).uid(config.DENORM_EVENTS_PRODUCER).setParallelism(config.downstreamOperatorsParallelism)
    denormStream.getSideOutput(config.denormFailedStatsTag).sinkTo(kafkaConnector.kafkaMapSink(config.failedStatsTopic))
      .name(config.DENORM_FAILED_STATS_PRODUCER).uid(config.DENORM_FAILED_STATS_PRODUCER).setParallelism(config.downstreamOperatorsParallelism)

    env.execute(config.jobName)
  }
}

// $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
object DenormalizerWindowStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("de-normalization.conf").withFallback(ConfigFactory.systemEnvironment()))
    val denormalizationConfig = new DenormalizerConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(denormalizationConfig)
    val task = new DenormalizerWindowStreamTask(denormalizationConfig, kafkaUtil)
    task.process()
  }
}
// $COVERAGE-ON$