package org.sunbird.obsrv.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.table.types.logical.VarCharType
import org.scalatest.Matchers._
import org.sunbird.obsrv.core.util.PostgresConnect
import org.sunbird.obsrv.spec.BaseSpecWithDatasetRegistry

import java.util.TimeZone

/**
 * Regression coverage for the bugs found in PR #131's review (manju + CodeRabbit):
 *  - VarCharType(isNullable, 20) truncating string columns -> VarCharType.MAX_LENGTH
 *  - unguarded .head on filtered collections for a missing partitionColumn -> clear
 *    IllegalArgumentException instead of a bare NoSuchElementException
 *  - jsonNode.at(...) returning MissingNode (not null) for an unresolved path, wrongly
 *    treated as "found" -> explicit isMissingNode/isNull checks in retrieveFieldFromJson
 *  - partition date conversion using the JVM default time zone instead of a fixed UTC,
 *    which could place the same instant in different Hudi partitions on different hosts
 */
class HudiSchemaParserTestSpec extends BaseSpecWithDatasetRegistry {

  private val jackson = new ObjectMapper()
  private var originalDefaultTimeZone: TimeZone = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    val postgresConnect = new PostgresConnect(postgresConfig)
    insertDataset(postgresConnect, "ds_ok")
    insertDatasource(postgresConnect, "ds_ok",
      """{"dataset":"ds_ok","schema":{"table":"ds_ok_table","partitionColumn":"event_ts","timestampColumn":"event_ts","primaryKey":"id","columnSpec":[{"name":"id","type":"string"},{"name":"event_ts","type":"epoch"},{"name":"payload","type":"string"}]},"inputFormat":{"type":"json","flattenSpec":{"fields":[{"type":"field","name":"id"},{"type":"field","name":"event_ts"},{"type":"field","name":"payload"}]}}}""")
    insertDataset(postgresConnect, "ds_ts_partition")
    insertDatasource(postgresConnect, "ds_ts_partition",
      """{"dataset":"ds_ts_partition","schema":{"table":"ds_ts_partition_table","partitionColumn":"when_col","timestampColumn":"when_col","primaryKey":"id","columnSpec":[{"name":"id","type":"string"},{"name":"when_col","type":"timestamp"}]},"inputFormat":{"type":"json","flattenSpec":{"fields":[{"type":"field","name":"id"},{"type":"field","name":"when_col"}]}}}""")
    postgresConnect.closeConnection()

    // Different from UTC in both hemispheres/offset directions, so a test asserting UTC-based
    // partitioning only actually proves it if the JVM default is something else - matches the
    // same explicit-TimeZone-override pattern already used in TestTimestampKeyParser.
    originalDefaultTimeZone = TimeZone.getDefault
    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
  }

  override def afterAll(): Unit = {
    TimeZone.setDefault(originalDefaultTimeZone)
    super.afterAll()
  }

  private def insertDataset(postgresConnect: PostgresConnect, id: String): Unit = {
    // datasources.dataset_id has a FK to datasets.id - minimal row satisfying every NOT NULL
    // column on the datasets table (schema created by BaseSpecWithDatasetRegistry).
    postgresConnect.execute(
      s"insert into datasets(id, type, router_config, dataset_config, status, api_version, entry_topic, created_by, updated_by, created_date, updated_date) values " +
        s"('$id', 'event', '{}', '{}', 'Live', 'v1', 'ingest', 'System', 'System', now(), now());"
    )
  }

  private def insertDatasource(postgresConnect: PostgresConnect, id: String, ingestionSpec: String): Unit = {
    val escaped = ingestionSpec.replace("'", "''")
    postgresConnect.execute(
      s"insert into datasources(id, dataset_id, type, ingestion_spec, datasource, datasource_ref, retention_period, archival_policy, purge_policy, backup_config, status, created_by, updated_by, created_date, updated_date) values " +
        s"('$id', '$id', 'datalake', '$escaped', '$id', '$id', '{}', '{}', '{}', '{}', 'Live', 'System', 'System', now(), now());"
    )
  }

  "createRowType (via readSchema)" should "use VarCharType.MAX_LENGTH for string columns, not the old hardcoded 20" in {
    val parser = new HudiSchemaParser()
    val rowType = parser.rowTypeMap("ds_ok")
    val payloadType = rowType.getFields.get(rowType.getFieldNames.indexOf("payload")).getType.asInstanceOf[VarCharType]
    payloadType.getLength should be(VarCharType.MAX_LENGTH)
  }

  it should "throw a clear IllegalArgumentException when partitionColumn isn't in columnSpec" in {
    val postgresConnect = new PostgresConnect(postgresConfig)
    insertDataset(postgresConnect, "ds_bad_partition")
    insertDatasource(postgresConnect, "ds_bad_partition",
      """{"dataset":"ds_bad_partition","schema":{"table":"t","partitionColumn":"does_not_exist","timestampColumn":"id","primaryKey":"id","columnSpec":[{"name":"id","type":"string"}]},"inputFormat":{"type":"json","flattenSpec":{"fields":[{"type":"field","name":"id"}]}}}""")

    try {
      val ex = intercept[IllegalArgumentException] {
        new HudiSchemaParser()
      }
      ex.getMessage should include("does_not_exist")
      ex.getMessage should include("ds_bad_partition")
    } finally {
      // getAllDatasources() returns every live datalake datasource - leaving this row in place
      // would make every later test's own `new HudiSchemaParser()` call throw on THIS entry too,
      // since readSchema()'s .map is eager/strict and aborts the whole load on the first bad one.
      postgresConnect.execute("delete from datasources where id = 'ds_bad_partition';")
      postgresConnect.execute("delete from datasets where id = 'ds_bad_partition';")
      postgresConnect.closeConnection()
    }
  }

  "retrieveFieldFromJson" should "return None for a direct field that's genuinely absent" in {
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"id":"rec1"}""")
    parser.retrieveFieldFromJson(node, JsonFieldParserSpec("field", "missingField")) should be(None)
  }

  it should "return Some for a direct field that's present" in {
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"id":"rec1"}""")
    parser.retrieveFieldFromJson(node, JsonFieldParserSpec("field", "id")) should not be None
  }

  it should "return None for a path that doesn't resolve, instead of Some(MissingNode)" in {
    // jsonNode.at(...) returns MissingNode (not Java null) for an unresolved pointer - before
    // the fix, Option(missingNode) evaluated to Some(missingNode), so this looked "found".
    // retrieveFieldFromJson drops expr's first segment and resolves the rest from jsonNode's
    // own root (field.expr.split(".").tail), so "root.value" looks up top-level "/value" -
    // "root" here is just a placeholder first segment, not an actual nesting level.
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"other":"x"}""")
    parser.retrieveFieldFromJson(node, JsonFieldParserSpec("path", "unused", Some("root.value"))) should be(None)
  }

  it should "return Some for a path that does resolve" in {
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"value":"present"}""")
    val result = parser.retrieveFieldFromJson(node, JsonFieldParserSpec("path", "unused", Some("root.value")))
    result should not be None
    result.get.asText() should be("present")
  }

  "parseJson" should "compute the epoch partition date in UTC, not the JVM default zone" in {
    val parser = new HudiSchemaParser()
    // 1700013600000 = 2023-11-15T02:00:00Z (verified via python3 datetime.fromtimestamp, not
    // hand arithmetic) - deliberately near a day boundary so a systemDefault()-based bug
    // (America/Los_Angeles, UTC-8 in November: 2023-11-15T02:00Z -> 2023-11-14T18:00 PST) would
    // compute 2023-11-14 instead of the correct UTC 2023-11-15.
    val event = """{"id":"rec1","event_ts":1700013600000,"payload":"hello"}"""
    val result = parser.parseJson("ds_ok", event)
    result("event_ts_partition") should be("2023-11-15")
  }

  it should "compute the timestamp partition date in UTC, not the JVM default zone" in {
    val parser = new HudiSchemaParser()
    val event = """{"id":"rec1","when_col":1700013600000}"""
    val result = parser.parseJson("ds_ts_partition", event)
    result("when_col_partition") should be("2023-11-15")
  }

}
