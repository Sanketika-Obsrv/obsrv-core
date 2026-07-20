package org.sunbird.obsrv.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.obsrv.dataproducts.MasterDataProcessorIndexer
import org.sunbird.obsrv.dataproducts.util.StorageUtil

// Lightweight tests for the master-data ingestion spec generation (no cluster/embedded infra).
// Verifies the dropExisting + MONTH replace spec is built correctly for a stable datasource_ref.
class MasterDataSpecGenSpec extends FlatSpec with Matchers {

  implicit val formats: Formats = DefaultFormats
  private val config: Config = ConfigFactory.load("masterdata-indexer-test.conf").withFallback(ConfigFactory.systemEnvironment())

  "getIngestionInterval" should "return the current month as a start/next-month range" in {
    val interval = StorageUtil.getIngestionInterval
    interval should fullyMatch regex """\d{4}-\d{2}-01/\d{4}-\d{2}-01"""
    val Array(start, end) = interval.split("/")
    start.substring(0, 7) should not equal end.substring(0, 7) // different month
    start.substring(8) shouldEqual "01"
    end.substring(8) shouldEqual "01"
  }

  "updateIngestionSpec" should "build a MONTH dropExisting replace spec bound to the datasource_ref" in {
    val ref = "test-dataset_events"
    val filePath = "s3a://bucket/masterdata-indexer/test-dataset/"
    val spec = MasterDataProcessorIndexer.updateIngestionSpec(ref, filePath, config)
    val json = parse(spec)

    withClue(s"generated spec: $spec\n") {
      // ingestion writes into the stable datasource_ref
      (json \ "spec" \ "dataSchema" \ "dataSource").extract[String] shouldEqual ref

      // replace-in-place: dropExisting true (appendToExisting left implicit — Druid defaults false)
      (json \ "spec" \ "ioConfig" \ "dropExisting").extract[Boolean] shouldEqual true

      // MONTH granularity + current-month interval (required by dropExisting)
      (json \ "spec" \ "dataSchema" \ "granularitySpec" \ "segmentGranularity").extract[String] shouldEqual "MONTH"
      (json \ "spec" \ "dataSchema" \ "granularitySpec" \ "intervals").extract[List[String]] shouldEqual List(StorageUtil.getIngestionInterval)

      // input source merged: INPUT_SOURCE_TYPE -> provider type (local in test), FILE_PATH -> path
      (json \ "spec" \ "ioConfig" \ "inputSource" \ "type").extract[String] shouldEqual "local"
      (json \ "spec" \ "ioConfig" \ "inputSource" \ "baseDir").extract[String] shouldEqual filePath
    }
  }

  it should "not carry a date-suffixed datasource_ref (stable name)" in {
    val ref = "test-dataset_events"
    val spec = MasterDataProcessorIndexer.updateIngestionSpec(ref, "s3a://bucket/path/", config)
    (parse(spec) \ "spec" \ "dataSchema" \ "dataSource").extract[String] shouldEqual ref
    spec should not include "_druid-"
  }
}
