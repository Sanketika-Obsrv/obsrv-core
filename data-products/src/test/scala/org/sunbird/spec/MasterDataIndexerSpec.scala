package org.sunbird.obsrv.spec

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig, duration2JavaDuration}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.MockitoSugar.mock
import org.sunbird.obsrv.registry.DatasetRegistry
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.sunbird.fixture.EventFixture
import org.sunbird.obsrv.core.cache.RedisConnect
import org.sunbird.obsrv.core.util.{PostgresConnect, PostgresConnectionConfig}
import org.sunbird.obsrv.dataproducts.helper.BaseMetricHelper
import org.sunbird.obsrv.dataproducts.model.{Edata, MetricLabel}
import redis.embedded.RedisServer
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.mockito.Mockito
import org.sunbird.obsrv.dataproducts.MasterDataProcessorIndexer
import org.sunbird.obsrv.dataproducts.util.{HttpUtil}

import scala.collection.mutable.ListBuffer

class MasterDataIndexerSpec extends FlatSpec with BeforeAndAfterAll with Matchers {

  private val jobConfig: Config = ConfigFactory.load("masterdata-indexer-test.conf").withFallback(ConfigFactory.systemEnvironment())
  implicit val httpUtil: HttpUtil = mock[HttpUtil](Mockito.withSettings().serializable())
  val mockMetrics = mock[BaseMetricHelper]
  val pwd = System.getProperty("user.dir")

  val postgresConfig = PostgresConnectionConfig(
    user = jobConfig.getString("postgres.user"),
    password = jobConfig.getString("postgres.password"),
    database = "postgres",
    host = jobConfig.getString("postgres.host"),
    port = jobConfig.getInt("postgres.port"),
    maxConnections = jobConfig.getInt("postgres.maxConnections")
  )

  var embeddedPostgres: EmbeddedPostgres = _
  var redisServer: RedisServer = _
  var redisConnection: RedisConnect = _
  private val dayPeriodFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC()
  val dt = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay()
  val date = dayPeriodFormat.print(dt)

  val customKafkaConsumerProperties: Map[String, String] = Map[String, String]("auto.offset.reset" -> "earliest", "group.id" -> "test-event-schema-group")
  implicit val embeddedKafkaConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(
      kafkaPort = 9092,
      zooKeeperPort = 2183,
      customConsumerProperties = customKafkaConsumerProperties
    )
  implicit val deserializer: StringDeserializer = new StringDeserializer()
  var spark: SparkSession = _


  override def beforeAll(): Unit = {
    super.beforeAll()
    val conf = new SparkConf()
      .setAppName("MasterDataProcessorIndexer")
      .setMaster("local[*]")
    spark = SparkSession.builder().config(conf).getOrCreate()
    redisServer = new RedisServer(6340)
    redisServer.start()
    embeddedPostgres = EmbeddedPostgres.builder.setPort(5432).start()
    val postgresConnect = new PostgresConnect(postgresConfig)
    createSchema(postgresConnect)
    insertTestData(postgresConnect)
    redisConnection = new RedisConnect("localhost", 6340, 30000)
    val jedis = redisConnection.getConnection(3)
    jedis.set("device-00", EventFixture.d1)
    jedis.set("device-01", EventFixture.d2)
    jedis.set("device-02", EventFixture.d3)
    jedis.set("device-03", EventFixture.d4)
    jedis.set("device-04", EventFixture.d5)
    EmbeddedKafka.start()(embeddedKafkaConfig)
    createTestTopics()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    redisServer.stop()
    EmbeddedKafka.stop()
    spark.stop()
  }

  def createTestTopics(): Unit = {
    EmbeddedKafka.createCustomTopic("spark.stats")
  }

  private def createSchema(postgresConnect: PostgresConnect) {
    postgresConnect.execute("CREATE TABLE IF NOT EXISTS datasets ( id text PRIMARY KEY, type text NOT NULL, validation_config json, extraction_config json, dedup_config json, data_schema json, denorm_config json, router_config json NOT NULL, dataset_config json NOT NULL, status text NOT NULL, tags text[], data_version INT, created_by text NOT NULL, updated_by text NOT NULL, created_date timestamp NOT NULL, updated_date timestamp NOT NULL );")
    postgresConnect.execute("CREATE TABLE IF NOT EXISTS datasources ( id text PRIMARY KEY, dataset_id text REFERENCES datasets (id), ingestion_spec json NOT NULL, datasource text NOT NULL, datasource_ref text NOT NULL);")
    postgresConnect.execute("CREATE TABLE IF NOT EXISTS dataset_transformations ( id text PRIMARY KEY, dataset_id text REFERENCES datasets (id), field_key text NOT NULL, transformation_function json NOT NULL, status text NOT NULL, mode text, created_by text NOT NULL, updated_by text NOT NULL, created_date Date NOT NULL, updated_date Date NOT NULL, UNIQUE(field_key, dataset_id) );")
    postgresConnect.execute("CREATE TABLE IF NOT EXISTS dataset_source_config ( id text PRIMARY KEY, dataset_id text NOT NULL REFERENCES datasets (id), connector_type text NOT NULL, connector_config json NOT NULL, status text NOT NULL, connector_stats json, created_by text NOT NULL, updated_by text NOT NULL, created_date Date NOT NULL, updated_date Date NOT NULL, UNIQUE(connector_type, dataset_id) );")
  }

  private def insertTestData(postgresConnect: PostgresConnect) = {
    postgresConnect.execute("insert into datasets(id, type, validation_config, extraction_config, dedup_config, data_schema, denorm_config, router_config, dataset_config, tags, data_version, status, created_by, updated_by, created_date, updated_date) VALUES('md1','master-dataset', '{\"validate\": true, \"mode\": \"Strict\", \"validation_mode\": {}}', '{\"is_batch_event\": true, \"extraction_key\": \"events\", \"dedup_config\": {\"drop_duplicates\": true, \"dedup_key\": \"id\", \"dedup_period\": 1036800}, \"batch_id\": \"id\"}', '{\"drop_duplicates\": true, \"dedup_key\": \"device_id\", \"dedup_period\": 1036800}', '{\"$schema\": \"https://json-schema.org/draft/2020-12/schema\", \"type\": \"object\", \"properties\": {\"fcm_token\": {\"type\": \"string\"}, \"city\": {\"type\": \"string\"}, \"device_id\": {\"type\": \"string\"}, \"device_spec\": {\"type\": \"string\"}, \"state\": {\"type\": \"string\"}, \"uaspec\": {\"type\": \"object\", \"properties\": {\"agent\": {\"type\": \"string\"}, \"ver\": {\"type\": \"string\"}, \"system\": {\"type\": \"string\"}, \"raw\": {\"type\": \"string\"}}}, \"country\": {\"type\": \"string\"}, \"country_code\": {\"type\": \"string\"}, \"producer_id\": {\"type\": \"string\"}, \"state_code_custom\": {\"type\": \"integer\"}, \"state_code\": {\"type\": \"string\"}, \"state_custom\": {\"type\": \"string\"}, \"district_custom\": {\"type\": \"string\"}, \"first_access\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''first_access'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"api_last_updated_on\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''api_last_updated_on'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"user_declared_district\": {\"type\": \"string\"}, \"user_declared_state\": {\"type\": \"string\"}}, \"required\": [\"first_access\", \"api_last_updated_on\", \"device_id\"]}', '{\"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"denorm_fields\": []}', '{\"topic\": \",d1\"}', '{\"data_key\": \"device_id\", \"timestamp_key\": \"\", \"exclude_fields\": [], \"entry_topic\": \"local.masterdata.ingest\", \"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"index_data\": true, \"redis_db\": 3}', NULL, NULL, 'Live', 'SYSTEM', 'SYSTEM', '2023-10-04 06:44:11.600', '2023-10-04 06:44:11.600');")
    postgresConnect.execute("insert into datasources(id, dataset_id, ingestion_spec, datasource, datasource_ref) VALUES('md1_md1.1_DAY', 'md1', '{\"type\": \"kafka\",\"spec\": {\"dataSchema\": {\"dataSource\": \"telemetry-device-data.1_DAY\",\"dimensionsSpec\": {\"dimensions\": [{\"type\": \"string\",\"name\": \"fcm_token\"},{\"type\": \"string\",\"name\": \"city\"},{\"type\": \"string\",\"name\": \"device_id\"},{\"type\": \"string\",\"name\": \"device_spec\"},{\"type\": \"string\",\"name\": \"state\"},{\"type\": \"string\",\"name\": \"uaspec_agent\"}]},\"timestampSpec\": {\"column\": \"syncts\",\"format\": \"auto\"},\"metricsSpec\": [{\"type\": \"doubleSum\",\"name\": \"state_code_custom\",\"fieldName\": \"state_code_custom\"}],\"granularitySpec\": {\"type\": \"uniform\",\"segmentGranularity\": \"DAY\",\"rollup\": false}},\"tuningConfig\": {\"type\": \"kafka\",\"maxBytesInMemory\": 134217728,\"maxRowsPerSegment\": 500000,\"logParseExceptions\": true},\"ioConfig\": {\"type\": \"kafka\",\"topic\": \"telemetry-device-data\",\"consumerProperties\": {\"bootstrap.servers\": \"localhost:9092\"},\"taskCount\": 1,\"replicas\": 1,\"taskDuration\": \"PT1H\",\"useEarliestOffset\": true,\"completionTimeout\": \"PT1H\",\"inputFormat\": {\"type\": \"json\",\"flattenSpec\": {\"useFieldDiscovery\": true,\"fields\": [{ \"type\": \"path\",\"expr\": \"$.fcm_token\",\"name\": \"fcm_token\"},{\"type\": \"path\",\"expr\": \"$.city\",\"name\": \"city\"},{\"type\": \"path\",\"expr\": \"$.device_id\",\"name\": \"device_id\"},{\"type\": \"path\",\"expr\": \"$.device_spec\",\"name\": \"device_spec\"},{\"type\": \"path\",\"expr\": \"$.state\",\"name\": \"state\"},{\"type\": \"path\",\"expr\": \"$.uaspec.agent\",\"name\": \"uaspec_agent\"}]}},\"appendToExisting\": false}}}', 'md1.1_DAY', 'md1.1_DAY');")
    postgresConnect.execute("insert into dataset_transformations values('tf1', 'md1', 'dealer.email', '{\"type\":\"mask\",\"expr\":\"dealer.email\"}', 'Live', 'Strict', 'System', 'System', now(), now());")
    postgresConnect.execute("insert into datasets(id, type, validation_config, extraction_config, dedup_config, denorm_config, router_config, dataset_config, tags, data_version, status, created_by, updated_by, created_date, updated_date) VALUES('md2','master-dataset', '{\"validate\": true, \"mode\": \"Strict\", \"validation_mode\": {}}', '{\"is_batch_event\": true, \"extraction_key\": \"events\", \"dedup_config\": {\"drop_duplicates\": true, \"dedup_key\": \"id\", \"dedup_period\": 1036800}, \"batch_id\": \"id\"}', '{\"drop_duplicates\": true, \"dedup_key\": \"device_id\", \"dedup_period\": 1036800}', '{\"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"denorm_fields\": []}', '{\"topic\": \",d1\"}', '{\"data_key\": \"device_id\", \"timestamp_key\": \"\", \"exclude_fields\": [], \"entry_topic\": \"local.masterdata.ingest\", \"redis_db_host\": \"localhost\", \"redis_db_port\":6340, \"index_data\": true, \"redis_db\": 5}', NULL, NULL, 'ACTIVE', 'SYSTEM', 'SYSTEM', 'now()', 'now()');")
    postgresConnect.execute("insert into datasources(id, dataset_id, ingestion_spec, datasource, datasource_ref) VALUES('md2_md1.1_DAY', 'md2', '{\"type\": \"kafka\",\"spec\": {\"dataSchema\": {\"dataSource\": \"telemetry-device-data.1_DAY\",\"dimensionsSpec\": {\"dimensions\": [{\"type\": \"string\",\"name\": \"fcm_token\"},{\"type\": \"string\",\"name\": \"city\"},{\"type\": \"string\",\"name\": \"device_id\"},{\"type\": \"string\",\"name\": \"device_spec\"},{\"type\": \"string\",\"name\": \"state\"},{\"type\": \"string\",\"name\": \"uaspec_agent\"}]},\"timestampSpec\": {\"column\": \"syncts\",\"format\": \"auto\"},\"metricsSpec\": [{\"type\": \"doubleSum\",\"name\": \"state_code_custom\",\"fieldName\": \"state_code_custom\"}],\"granularitySpec\": {\"type\": \"uniform\",\"segmentGranularity\": \"DAY\",\"rollup\": false}},\"tuningConfig\": {\"type\": \"kafka\",\"maxBytesInMemory\": 134217728,\"maxRowsPerSegment\": 500000,\"logParseExceptions\": true},\"ioConfig\": {\"type\": \"kafka\",\"topic\": \"telemetry-device-data\",\"consumerProperties\": {\"bootstrap.servers\": \"localhost:9092\"},\"taskCount\": 1,\"replicas\": 1,\"taskDuration\": \"PT1H\",\"useEarliestOffset\": true,\"completionTimeout\": \"PT1H\",\"inputFormat\": {\"type\": \"json\",\"flattenSpec\": {\"useFieldDiscovery\": true,\"fields\": [{ \"type\": \"path\",\"expr\": \"$.fcm_token\",\"name\": \"fcm_token\"},{\"type\": \"path\",\"expr\": \"$.city\",\"name\": \"city\"},{\"type\": \"path\",\"expr\": \"$.device_id\",\"name\": \"device_id\"},{\"type\": \"path\",\"expr\": \"$.device_spec\",\"name\": \"device_spec\"},{\"type\": \"path\",\"expr\": \"$.state\",\"name\": \"state\"},{\"type\": \"path\",\"expr\": \"$.uaspec.agent\",\"name\": \"uaspec_agent\"}]}},\"appendToExisting\": false}}}', 'md2.1_DAY', 'md2.1_DAY');")
    postgresConnect.execute("insert into datasets(id, type, validation_config, extraction_config, dedup_config, data_schema, denorm_config, router_config, dataset_config, tags, data_version, status, created_by, updated_by, created_date, updated_date) VALUES('md3','master-dataset', '{\"validate\": true, \"mode\": \"Strict\", \"validation_mode\": {}}', '{\"is_batch_event\": true, \"extraction_key\": \"events\", \"dedup_config\": {\"drop_duplicates\": true, \"dedup_key\": \"id\", \"dedup_period\": 1036800}, \"batch_id\": \"id\"}', '{\"drop_duplicates\": true, \"dedup_key\": \"device_id\", \"dedup_period\": 1036800}', '{\"$schema\": \"https://json-schema.org/draft/2020-12/schema\", \"type\": \"object\", \"properties\": {\"fcm_token\": {\"type\": \"string\"}, \"city\": {\"type\": \"string\"}, \"device_id\": {\"type\": \"string\"}, \"device_spec\": {\"type\": \"string\"}, \"state\": {\"type\": \"string\"}, \"uaspec\": {\"type\": \"object\", \"properties\": {\"agent\": {\"type\": \"string\"}, \"ver\": {\"type\": \"string\"}, \"system\": {\"type\": \"string\"}, \"raw\": {\"type\": \"string\"}}}, \"country\": {\"type\": \"string\"}, \"country_code\": {\"type\": \"string\"}, \"producer_id\": {\"type\": \"string\"}, \"state_code_custom\": {\"type\": \"integer\"}, \"state_code\": {\"type\": \"string\"}, \"state_custom\": {\"type\": \"string\"}, \"district_custom\": {\"type\": \"string\"}, \"first_access\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''first_access'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"api_last_updated_on\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''api_last_updated_on'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"user_declared_district\": {\"type\": \"string\"}, \"user_declared_state\": {\"type\": \"string\"}}, \"required\": [\"first_access\", \"api_last_updated_on\", \"device_id\"]}', '{\"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"denorm_fields\": []}', '{\"topic\": \",d1\"}', '{\"data_key\": \"device_id\", \"timestamp_key\": \"\", \"exclude_fields\": [], \"entry_topic\": \"local.masterdata.ingest\", \"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"index_data\": true, \"redis_db\": 6}', NULL, NULL, 'Live', 'SYSTEM', 'SYSTEM', '2023-10-04 06:44:11.600', '2023-10-04 06:44:11.600');")
    postgresConnect.execute("insert into datasources(id, dataset_id, ingestion_spec, datasource, datasource_ref) VALUES('md3_md3.1_DAY', 'md3', '{\"type\": \"kafka\",\"spec\": {\"dataSchema\": {\"dataSource\": \"telemetry-device-data.1_DAY\",\"dimensionsSpec\": {\"dimensions\": [{\"type\": \"string\",\"name\": \"fcm_token\"},{\"type\": \"string\",\"name\": \"city\"},{\"type\": \"string\",\"name\": \"device_id\"},{\"type\": \"string\",\"name\": \"device_spec\"},{\"type\": \"string\",\"name\": \"state\"},{\"type\": \"string\",\"name\": \"uaspec_agent\"}]},\"timestampSpec\": {\"column\": \"syncts\",\"format\": \"auto\"},\"metricsSpec\": [{\"type\": \"doubleSum\",\"name\": \"state_code_custom\",\"fieldName\": \"state_code_custom\"}],\"granularitySpec\": {\"type\": \"uniform\",\"segmentGranularity\": \"DAY\",\"rollup\": false}},\"tuningConfig\": {\"type\": \"kafka\",\"maxBytesInMemory\": 134217728,\"maxRowsPerSegment\": 500000,\"logParseExceptions\": true},\"ioConfig\": {\"type\": \"kafka\",\"topic\": \"telemetry-device-data\",\"consumerProperties\": {\"bootstrap.servers\": \"localhost:9092\"},\"taskCount\": 1,\"replicas\": 1,\"taskDuration\": \"PT1H\",\"useEarliestOffset\": true,\"completionTimeout\": \"PT1H\",\"inputFormat\": {\"type\": \"json\",\"flattenSpec\": {\"useFieldDiscovery\": true,\"fields\": [{ \"type\": \"path\",\"expr\": \"$.fcm_token\",\"name\": \"fcm_token\"},{\"type\": \"path\",\"expr\": \"$.city\",\"name\": \"city\"},{\"type\": \"path\",\"expr\": \"$.device_id\",\"name\": \"device_id\"},{\"type\": \"path\",\"expr\": \"$.device_spec\",\"name\": \"device_spec\"},{\"type\": \"path\",\"expr\": \"$.state\",\"name\": \"state\"},{\"type\": \"path\",\"expr\": \"$.uaspec.agent\",\"name\": \"uaspec_agent\"}]}},\"appendToExisting\": false}}}', 'md3.1_DAY', 'md3.1_DAY');")
    postgresConnect.execute("insert into datasources(id, dataset_id, ingestion_spec, datasource, datasource_ref) VALUES('md3_md3.2_DAY', 'md3', '{\"type\": \"kafka\",\"spec\": {\"dataSchema\": {\"dataSource\": \"telemetry-device-data.1_DAY\",\"dimensionsSpec\": {\"dimensions\": [{\"type\": \"string\",\"name\": \"fcm_token\"},{\"type\": \"string\",\"name\": \"city\"},{\"type\": \"string\",\"name\": \"device_id\"},{\"type\": \"string\",\"name\": \"device_spec\"},{\"type\": \"string\",\"name\": \"state\"},{\"type\": \"string\",\"name\": \"uaspec_agent\"}]},\"timestampSpec\": {\"column\": \"syncts\",\"format\": \"auto\"},\"metricsSpec\": [{\"type\": \"doubleSum\",\"name\": \"state_code_custom\",\"fieldName\": \"state_code_custom\"}],\"granularitySpec\": {\"type\": \"uniform\",\"segmentGranularity\": \"DAY\",\"rollup\": false}},\"tuningConfig\": {\"type\": \"kafka\",\"maxBytesInMemory\": 134217728,\"maxRowsPerSegment\": 500000,\"logParseExceptions\": true},\"ioConfig\": {\"type\": \"kafka\",\"topic\": \"telemetry-device-data\",\"consumerProperties\": {\"bootstrap.servers\": \"localhost:9092\"},\"taskCount\": 1,\"replicas\": 1,\"taskDuration\": \"PT1H\",\"useEarliestOffset\": true,\"completionTimeout\": \"PT1H\",\"inputFormat\": {\"type\": \"json\",\"flattenSpec\": {\"useFieldDiscovery\": true,\"fields\": [{ \"type\": \"path\",\"expr\": \"$.fcm_token\",\"name\": \"fcm_token\"},{\"type\": \"path\",\"expr\": \"$.city\",\"name\": \"city\"},{\"type\": \"path\",\"expr\": \"$.device_id\",\"name\": \"device_id\"},{\"type\": \"path\",\"expr\": \"$.device_spec\",\"name\": \"device_spec\"},{\"type\": \"path\",\"expr\": \"$.state\",\"name\": \"state\"},{\"type\": \"path\",\"expr\": \"$.uaspec.agent\",\"name\": \"uaspec_agent\"}]}},\"appendToExisting\": false}}}', 'md3.2_DAY', 'md3.2_DAY');")
    postgresConnect.execute("insert into datasets(id, type, validation_config, extraction_config, dedup_config, data_schema, denorm_config, router_config, dataset_config, tags, data_version, status, created_by, updated_by, created_date, updated_date) VALUES('md5','master-dataset', '{\"validate\": true, \"mode\": \"Strict\", \"validation_mode\": {}}', '{\"is_batch_event\": true, \"extraction_key\": \"events\", \"dedup_config\": {\"drop_duplicates\": true, \"dedup_key\": \"id\", \"dedup_period\": 1036800}, \"batch_id\": \"id\"}', '{\"drop_duplicates\": true, \"dedup_key\": \"device_id\", \"dedup_period\": 1036800}', '{\"$schema\": \"https://json-schema.org/draft/2020-12/schema\", \"type\": \"object\", \"properties\": {\"fcm_token\": {\"type\": \"string\"}, \"city\": {\"type\": \"string\"}, \"device_id\": {\"type\": \"string\"}, \"device_spec\": {\"type\": \"string\"}, \"state\": {\"type\": \"string\"}, \"uaspec\": {\"type\": \"object\", \"properties\": {\"agent\": {\"type\": \"string\"}, \"ver\": {\"type\": \"string\"}, \"system\": {\"type\": \"string\"}, \"raw\": {\"type\": \"string\"}}}, \"country\": {\"type\": \"string\"}, \"country_code\": {\"type\": \"string\"}, \"producer_id\": {\"type\": \"string\"}, \"state_code_custom\": {\"type\": \"integer\"}, \"state_code\": {\"type\": \"string\"}, \"state_custom\": {\"type\": \"string\"}, \"district_custom\": {\"type\": \"string\"}, \"first_access\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''first_access'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"api_last_updated_on\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''api_last_updated_on'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"user_declared_district\": {\"type\": \"string\"}, \"user_declared_state\": {\"type\": \"string\"}}, \"required\": [\"first_access\", \"api_last_updated_on\", \"device_id\"]}', '{\"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"denorm_fields\": []}', '{\"topic\": \",d1\"}', '{\"data_key\": \"device_id\", \"timestamp_key\": \"\", \"exclude_fields\": [], \"entry_topic\": \"local.masterdata.ingest\", \"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"index_data\": true, \"redis_db\": 9}', NULL, NULL, 'Live', 'SYSTEM', 'SYSTEM', '2023-10-04 06:44:11.600', '2023-10-04 06:44:11.600');")
    postgresConnect.execute("insert into datasets(id, type, validation_config, extraction_config, dedup_config, data_schema, denorm_config, router_config, dataset_config, tags, data_version, status, created_by, updated_by, created_date, updated_date) VALUES('md4','master-dataset', '{\"validate\": true, \"mode\": \"Strict\", \"validation_mode\": {}}', '{\"is_batch_event\": true, \"extraction_key\": \"events\", \"dedup_config\": {\"drop_duplicates\": true, \"dedup_key\": \"id\", \"dedup_period\": 1036800}, \"batch_id\": \"id\"}', '{\"drop_duplicates\": true, \"dedup_key\": \"device_id\", \"dedup_period\": 1036800}', '{\"$schema\": \"https://json-schema.org/draft/2020-12/schema\", \"type\": \"object\", \"properties\": {\"fcm_token\": {\"type\": \"string\"}, \"city\": {\"type\": \"string\"}, \"device_id\": {\"type\": \"string\"}, \"device_spec\": {\"type\": \"string\"}, \"state\": {\"type\": \"string\"}, \"uaspec\": {\"type\": \"object\", \"properties\": {\"agent\": {\"type\": \"string\"}, \"ver\": {\"type\": \"string\"}, \"system\": {\"type\": \"string\"}, \"raw\": {\"type\": \"string\"}}}, \"country\": {\"type\": \"string\"}, \"country_code\": {\"type\": \"string\"}, \"producer_id\": {\"type\": \"string\"}, \"state_code_custom\": {\"type\": \"integer\"}, \"state_code\": {\"type\": \"string\"}, \"state_custom\": {\"type\": \"string\"}, \"district_custom\": {\"type\": \"string\"}, \"first_access\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''first_access'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"api_last_updated_on\": {\"type\": \"integer\", \"format\": \"date-time\", \"suggestions\": [{\"message\": \"The Property ''api_last_updated_on'' appears to be ''date-time'' format type.\", \"advice\": \"The System can index all data on this column\", \"resolutionType\": \"INDEX\", \"severity\": \"LOW\"}]}, \"user_declared_district\": {\"type\": \"string\"}, \"user_declared_state\": {\"type\": \"string\"}}, \"required\": [\"first_access\", \"api_last_updated_on\", \"device_id\"]}', '{\"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"denorm_fields\": []}', '{\"topic\": \",d1\"}', '{\"data_key\": \"device_id\", \"timestamp_key\": \"\", \"exclude_fields\": [], \"entry_topic\": \"local.masterdata.ingest\", \"redis_db_host\": \"localhost\", \"redis_db_port\": 6340, \"index_data\": true, \"redis_db\": 3}', NULL, NULL, 'Live', 'SYSTEM', 'SYSTEM', '2023-10-04 06:44:11.600', '2023-10-04 06:44:11.600');")
    postgresConnect.execute("insert into datasources(id, dataset_id, ingestion_spec, datasource, datasource_ref) VALUES('md4_md4.1_DAY', 'md4', '{\"type\": \"kafka\",\"spec\": {\"dataSchema\": {\"dataSource\": \"telemetry-device-data.1_DAY\",\"dimensionsSpec\": {\"dimensions\": [{\"type\": \"string\",\"name\": \"fcm_token\"},{\"type\": \"string\",\"name\": \"city\"},{\"type\": \"string\",\"name\": \"device_id\"},{\"type\": \"string\",\"name\": \"device_spec\"},{\"type\": \"string\",\"name\": \"state\"},{\"type\": \"string\",\"name\": \"uaspec_agent\"}]},\"timestampSpec\": {\"column\": \"syncts\",\"format\": \"auto\"},\"metricsSpec\": [{\"type\": \"doubleSum\",\"name\": \"state_code_custom\",\"fieldName\": \"state_code_custom\"}],\"granularitySpec\": {\"type\": \"uniform\",\"segmentGranularity\": \"DAY\",\"rollup\": false}},\"tuningConfig\": {\"type\": \"kafka\",\"maxBytesInMemory\": 134217728,\"maxRowsPerSegment\": 500000,\"logParseExceptions\": true},\"ioConfig\": {\"type\": \"kafka\",\"topic\": \"telemetry-device-data\",\"consumerProperties\": {\"bootstrap.servers\": \"localhost:9092\"},\"taskCount\": 1,\"replicas\": 1,\"taskDuration\": \"PT1H\",\"useEarliestOffset\": true,\"completionTimeout\": \"PT1H\",\"inputFormat\": {\"type\": \"json\",\"flattenSpec\": {\"useFieldDiscovery\": true,\"fields\": [{ \"type\": \"path\",\"expr\": \"$.fcm_token\",\"name\": \"fcm_token\"},{\"type\": \"path\",\"expr\": \"$.city\",\"name\": \"city\"},{\"type\": \"path\",\"expr\": \"$.device_id\",\"name\": \"device_id\"},{\"type\": \"path\",\"expr\": \"$.device_spec\",\"name\": \"device_spec\"},{\"type\": \"path\",\"expr\": \"$.state\",\"name\": \"state\"},{\"type\": \"path\",\"expr\": \"$.uaspec.agent\",\"name\": \"uaspec_agent\"}]}},\"appendToExisting\": false}}}', 'md4.1_DAY', 'md4.1_DAY');")

  }

  def checkTestTopicsOffset(): Unit = {
    val topics: java.util.Collection[String] = new java.util.ArrayList[String]()
    topics.add("spark.stats")
    val consumerPollingTimeout: FiniteDuration = FiniteDuration(1, "minute")
    EmbeddedKafka.withConsumer[String, String, Unit] {
      val messagesBuffers = topics.asScala.map(_ -> ListBuffer.empty[(String, String)]).toMap
      consumer =>
        consumer.subscribe(topics)
        val recordIterator = consumer.poll(duration2JavaDuration(consumerPollingTimeout)).iterator()
        while (recordIterator.hasNext) {
          val record = recordIterator.next
          messagesBuffers(record.topic) += (record.key() -> record.value())
          consumer.commitSync()
        }
        consumer.close()
        val messages = messagesBuffers.mapValues(_.toList)
        messages("spark.stats").length shouldBe 4

    }
  }

  it should "index datasets for single datasource and generate metrics for local storage" in {
    val provider = jobConfig.withValue("cloudStorage.container", ConfigValueFactory.fromAnyRef(s"${pwd}/obsrv-data"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
  }

  it should "index datasets for aws" in {
    val provider = jobConfig.withValue("cloudStorage.provider", ConfigValueFactory.fromAnyRef("aws"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    val edata = Edata(metric = Map(mockMetrics.getMetricName("failure_dataset_count") -> 1, mockMetrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.get.id), MetricLabel("cloud", s"${provider.getString("cloudStorage.provider")}")), err = "FAILED", errMsg = " The bucketName parameter must be specified.")
  }

  it should "index datasets for azure" in {
    val provider = jobConfig.withValue("cloudStorage.provider", ConfigValueFactory.fromAnyRef("azure"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    val edata = Edata(metric = Map(mockMetrics.getMetricName("failure_dataset_count") -> 1, mockMetrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.get.id), MetricLabel("cloud", s"${provider.getString("cloudStorage.provider")}")), err = "FAILED", errMsg = "No FileSystem for scheme: wasbs")
  }

  it should "index datasets for gcloud" in {
    val provider = jobConfig.withValue("cloudStorage.provider", ConfigValueFactory.fromAnyRef("gcloud"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    val edata = Edata(metric = Map(mockMetrics.getMetricName("failure_dataset_count") -> 1, mockMetrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.get.id), MetricLabel("cloud", s"${provider.getString("cloudStorage.provider")}")), err = "FAILED", errMsg = " The bucketName parameter must be specified.")
  }

  it should "index datasets for cephs3" in {
    val provider = jobConfig.withValue("cloudStorage.provider", ConfigValueFactory.fromAnyRef("cephs3"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    val edata = Edata(metric = Map(mockMetrics.getMetricName("failure_dataset_count") -> 1, mockMetrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.get.id), MetricLabel("cloud", s"${provider.getString("cloudStorage.provider")}")), err = "FAILED", errMsg = " The bucketName parameter must be specified.")
  }

  it should "index datasets for oci" in {
    val provider = jobConfig.withValue("cloudStorage.provider", ConfigValueFactory.fromAnyRef("oci"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    val edata = Edata(metric = Map(mockMetrics.getMetricName("failure_dataset_count") -> 1, mockMetrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.get.id), MetricLabel("cloud", s"${provider.getString("cloudStorage.provider")}")), err = "FAILED", errMsg = " The bucketName parameter must be specified.")
  }

  it should "not index datasets for unknown provider" in {
    val provider = jobConfig.withValue("cloudStorage.provider", ConfigValueFactory.fromAnyRef("ibm"))
    val dataset = DatasetRegistry.getDataset("md1")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    val edata = Edata(metric = Map(mockMetrics.getMetricName("failure_dataset_count") -> 1, mockMetrics.getMetricName("total_dataset_count") -> 1), labels = List(MetricLabel("job", "MasterDataIndexer"), MetricLabel("datasetId", dataset.get.id), MetricLabel("cloud", s"${provider.getString("cloudStorage.provider")}")), err = "FAILED", errMsg = "Unsupported provider")
  }

  it should "throw exception when datasource is null" in {
    val provider = jobConfig.withValue("cloudStorage.container", ConfigValueFactory.fromAnyRef(s"${pwd}/obsrv-data"))
    val datasource = DatasetRegistry.getDatasources("md5")
    MasterDataProcessorIndexer.processDatasets(provider, spark)
    assert(datasource.get.size == 0)
  }

  it should "throw exception when submitting ingestion spec" in {
    val provider = jobConfig.withValue("cloudStorage.container", ConfigValueFactory.fromAnyRef(s"${pwd}/obsrv-data"))
    val ingestionSpec = s"""{"type":"index_parallel","spec":{"dataSchema":{"dataSource":"datasource1-${date}"},"ioConfig":{"type":"index_parallel","inputSource":{"type":"local","baseDir":"/home/sankethika/obsrv-data","filter":"**.json.gz"}},"tuningConfig":{"type":"index_parallel","maxRowsInMemory":500000,"forceExtendableShardSpecs":false,"logParseExceptions":true}}}"""
    MasterDataProcessorIndexer.processDatasets(provider, spark)
  }
}
