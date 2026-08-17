package org.sunbird.obsrv.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.table.types.logical.VarCharType
import org.scalatest.Matchers._
import org.sunbird.obsrv.core.util.PostgresConnect
import org.sunbird.obsrv.spec.BaseSpecWithDatasetRegistry

import java.util.TimeZone

/**
 * Regression coverage for HudiSchemaParser:
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
    insertDataset(postgresConnect, "financial_transactions")
    // Exact content of src/main/resources/schemas/schema.json, the connector's own real shipped
    // example - end-to-end proof that none of this round's changes broke the actual production
    // shape: "root" (non-"path") field types, "$."-prefixed nested paths, a string (not
    // timestamp/epoch) partitionColumn.
    insertDatasource(postgresConnect, "financial_transactions",
      """{"dataset": "financial_transactions", "schema": {"table": "financial_transactions", "partitionColumn": "receiver_ifsc_code", "timestampColumn": "txn_date", "primaryKey": "txn_id", "columnSpec": [{"name": "receiver_account_number", "type": "string"}, {"name": "receiver_ifsc_code", "type": "string"}, {"name": "sender_account_number", "type": "string"}, {"name": "sender_contact_email", "type": "string"}, {"name": "sender_ifsc_code", "type": "string"}, {"name": "currency", "type": "string"}, {"name": "txn_amount", "type": "int"}, {"name": "txn_date", "type": "string"}, {"name": "txn_id", "type": "string"}, {"name": "txn_status", "type": "string"}, {"name": "txn_type", "type": "string"}]}, "inputFormat": {"type": "json", "flattenSpec": {"fields": [{"type": "root", "name": "receiver_account_number"}, {"type": "path", "name": "sender_account_number", "expr": "$.sender.account_number"}, {"type": "path", "name": "sender_ifsc_code", "expr": "$.sender.ifsc_code"}, {"type": "root", "name": "receiver_ifsc_code"}, {"type": "root", "name": "sender_contact_email"}, {"type": "root", "name": "currency"}, {"type": "root", "name": "txn_amount"}, {"type": "root", "name": "txn_date"}, {"type": "root", "name": "txn_id"}, {"type": "root", "name": "txn_status"}, {"type": "root", "name": "txn_type"}]}}}""")
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

  it should "return None for a \"$.\" path that doesn't resolve, instead of Some(MissingNode)" in {
    // jsonNode.at(...) returns MissingNode (not Java null) for an unresolved pointer - before
    // the fix, Option(missingNode) evaluated to Some(missingNode), so this looked "found".
    // "$." is the real convention this connector's own shipped example uses
    // (schemas/schema.json: "$.sender.account_number") - "$" is a root indicator, stripped;
    // the rest is a real nesting path.
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"sender":{"other":"x"}}""")
    parser.retrieveFieldFromJson(node, JsonFieldParserSpec("path", "unused", Some("$.sender.account_number"))) should be(None)
  }

  it should "return Some for a \"$.\" path that does resolve (matches schemas/schema.json's own convention)" in {
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"sender":{"account_number":"12345"}}""")
    val result = parser.retrieveFieldFromJson(node, JsonFieldParserSpec("path", "unused", Some("$.sender.account_number")))
    result should not be None
    result.get.asText() should be("12345")
  }

  it should "resolve a bare dotted path (no \"$.\" prefix) using every segment, not dropping the first" in {
    // Regression for a real bug: the old code unconditionally dropped expr's first segment
    // regardless of whether it started with "$" - "actor.id" would drop "actor" and look up
    // top-level "/id" instead of the actually-nested "/actor/id".
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"actor":{"id":"u1"}, "id":"wrong-if-first-segment-dropped"}""")
    val result = parser.retrieveFieldFromJson(node, JsonFieldParserSpec("path", "unused", Some("actor.id")))
    result should not be None
    result.get.asText() should be("u1")
  }

  it should "resolve a single-segment bare path (no dot at all)" in {
    // Regression: the old code's .tail on a 1-element split() returned an empty array,
    // building pointer "/" (looks up a literal empty-string key at root) instead of "/id".
    val parser = new HudiSchemaParser()
    val node = jackson.readTree("""{"id":"rec1"}""")
    val result = parser.retrieveFieldFromJson(node, JsonFieldParserSpec("path", "unused", Some("id")))
    result should not be None
    result.get.asText() should be("rec1")
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

  it should "compute the timestamp partition date in UTC for a numeric (epoch-millis) timestamp value" in {
    val parser = new HudiSchemaParser()
    val event = """{"id":"rec1","when_col":1700013600000}"""
    val result = parser.parseJson("ds_ts_partition", event)
    result("when_col_partition") should be("2023-11-15")
  }

  it should "parse a \"Z\"-terminated ISO-8601 string timestamp value" in {
    // Verified empirically against jackson-databind:2.17.2 (this project's own pinned version):
    // objectMapper.treeToValue(..., classOf[Timestamp]) already handles this correctly via its
    // own lenient StdDateFormat - no special-casing needed for this specific shape.
    val parser = new HudiSchemaParser()
    val event = """{"id":"rec1","when_col":"2023-11-15T02:00:00Z"}"""
    val result = parser.parseJson("ds_ts_partition", event)
    result("when_col_partition") should be("2023-11-15")
    result("when_col") should be("2023-11-15T02:00:00Z")
  }

  it should "parse an explicit-offset ISO-8601 string timestamp value" in {
    // "+05:30" instead of "Z" - also handled fine by Jackson's own deserializer (verified);
    // Instant.parse alone (an earlier, since-corrected version of this fix) does NOT accept
    // this format and would have regressed it.
    val parser = new HudiSchemaParser()
    val event = """{"id":"rec1","when_col":"2023-11-15T07:30:00+05:30"}"""
    val result = parser.parseJson("ds_ts_partition", event)
    result("when_col_partition") should be("2023-11-15")
  }

  it should "parse a SQL-format (space-separated) timestamp string via the Timestamp.valueOf fallback (regression: this used to throw)" in {
    // The one representation Jackson's own Timestamp deserializer genuinely can't parse -
    // confirmed via direct testing it throws InvalidFormatException for this exact shape,
    // aborting this whole try block, skipping the "_partition" put (non-nullable in the
    // schema) and crashing downstream in JsonToRowDataConverter with a NullPointerException.
    // Falls back to java.sql.Timestamp.valueOf, the JDK's own parser for exactly this format.
    val parser = new HudiSchemaParser()
    val event = """{"id":"rec1","when_col":"2023-11-15 02:00:00"}"""
    val result = parser.parseJson("ds_ts_partition", event)
    result("when_col_partition") should be("2023-11-15")
  }

  "the connector's own real shipped example (schemas/schema.json)" should "still create the expected RowType" in {
    val parser = new HudiSchemaParser()
    val rowType = parser.rowTypeMap("financial_transactions")
    // partitionColumn (receiver_ifsc_code) is type "string", not timestamp/epoch - no derived
    // "_partition" column should be added, unlike the ds_ok/ds_ts_partition datasets above.
    rowType.getFieldNames.contains("receiver_ifsc_code_partition") should be(false)
    rowType.getFieldNames.size should be(11)
    val amountType = rowType.getFields.get(rowType.getFieldNames.indexOf("txn_amount")).getType
    amountType.asInstanceOf[org.apache.flink.table.types.logical.IntType].isNullable should be(true)
    val txnIdType = rowType.getFields.get(rowType.getFieldNames.indexOf("txn_id")).getType
    txnIdType.asInstanceOf[VarCharType].isNullable should be(false) // primaryKey
  }

  it should "parse a realistic event end to end - both \"root\" and \"$.\"-nested \"path\" fields" in {
    val parser = new HudiSchemaParser()
    val event =
      """{
        |  "receiver_account_number": "ACC001",
        |  "receiver_ifsc_code": "HDFC0001",
        |  "sender": {"account_number": "ACC999", "ifsc_code": "ICIC0002"},
        |  "sender_contact_email": "sender@example.com",
        |  "currency": "INR",
        |  "txn_amount": 5000,
        |  "txn_date": "2023-11-15",
        |  "txn_id": "TXN123",
        |  "txn_status": "SUCCESS",
        |  "txn_type": "TRANSFER"
        |}""".stripMargin
    val result = parser.parseJson("financial_transactions", event)
    // "root" type fields (direct top-level lookup)
    result("receiver_account_number") should be("ACC001")
    result("receiver_ifsc_code") should be("HDFC0001")
    result("txn_amount") should be(5000)
    result("txn_id") should be("TXN123")
    // "path" type fields with a "$."-prefixed expr, nested under "sender"
    result("sender_account_number") should be("ACC999")
    result("sender_ifsc_code") should be("ICIC0002")
    // string partitionColumn - no derived "_partition" key
    result.contains("receiver_ifsc_code_partition") should be(false)
  }

}
