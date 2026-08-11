package org.sunbird.obsrv.router

import org.joda.time.DateTimeZone
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.{DatasetConfig, IndexingConfig, KeysConfig}
import org.sunbird.obsrv.router.functions.TimestampKeyParser

import java.util.TimeZone
import scala.collection.mutable

class TestTimestampKeyParser extends FlatSpec with Matchers with BeforeAndAfterAll {

  // TimestampKeyParser.addTimeZone (and DateTimeFormat.parseDateTime, when no explicit
  // datasetTimezone is set) both fall back to the ambient default timezone - every hardcoded
  // expected value below is only correct if that default is IST (Asia/Kolkata), which is what
  // this suite was authored/last verified against. On a CI runner defaulting to UTC,
  // "2023-03-01" (no timezone) parses to UTC midnight instead of IST midnight, and every
  // addTimeZone(...) result shifts by the same 5:30 offset - failing every assertion below, not
  // just one. Setting java.util.TimeZone.setDefault() alone isn't enough - Joda-Time (which
  // TimestampKeyParser actually uses, via DateTime/DateTimeFormat) caches its own default
  // DateTimeZone independently and doesn't reliably pick up changes to java.util.TimeZone's
  // default afterward (verified: still failed under TZ=UTC with only java.util.TimeZone set).
  // Pinning both defaults (and restoring both afterward, so this doesn't leak into other suites
  // sharing the same JVM/reactor) makes this suite deterministic without touching production
  // code or any of the literal expected values.
  private var originalDefaultTimeZone: TimeZone = _
  private var originalDefaultDateTimeZone: DateTimeZone = _

  override def beforeAll(): Unit = {
    originalDefaultTimeZone = TimeZone.getDefault
    originalDefaultDateTimeZone = DateTimeZone.getDefault
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    DateTimeZone.setDefault(DateTimeZone.forID("Asia/Kolkata"))
  }

  override def afterAll(): Unit = {
    TimeZone.setDefault(originalDefaultTimeZone)
    DateTimeZone.setDefault(originalDefaultDateTimeZone)
  }

  "TimestampKeyParser" should "validate all scenarios of timestamp key in number format" in {


    // Validate text date field without providing dateformat and timezone
    val result1 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"2023-03-01"}"""))
    result1.isValid should be(true)
    result1.value.asInstanceOf[String] should be("2023-03-01")

    // Validate missing timestamp key scenario
    val result2 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date1"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"2023-03-01"}"""))
    result2.isValid should be(false)
    result2.value should be(null)

    // Validate number date field which is not epoch
    val result3 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":20232201}"""))
    result3.isValid should be(false)
    result3.value.asInstanceOf[Int] should be(0)

    // Validate number date field which is epoch in seconds
    val result4 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":1701373165}"""))
    result4.isValid should be(true)
    result4.value.asInstanceOf[Long] should be(1701373165000L)

    // Validate number date field which is epoch in milli-seconds
    val result5 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":1701373165123}"""))
    result5.isValid should be(true)
    result5.value.asInstanceOf[Long] should be(1701373165123L)

    // Validate number date field which is epoch in micro-seconds
    val result6 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":1701373165123111}"""))
    result6.isValid should be(true)
    result6.value.asInstanceOf[Long] should be(1701373165123L)

    // Validate number date field which is epoch in nano-seconds
    val result7 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":1701373165123111000}"""))
    result7.isValid should be(true)
    result7.value.asInstanceOf[Long] should be(1701373165123L)

    // Validate number date field which is not an epoch in milli, micro or nano seconds
    val result8 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None)),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":170137316512}"""))
    result8.isValid should be(false)
    result8.value.asInstanceOf[Int] should be(0)

    // Validate number date field which is an epoch with timezone present
    val result9 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), None), datasetTimezone = Some("GMT+05:30")),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":1701373165123}"""))
    result9.isValid should be(true)
    result9.value.asInstanceOf[Long] should be(1701392965123L)
  }

  it should "validate all scenarios of timestamp key in text format" in {

    // Validate epoch data in text format
    val result1 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), Some("epoch")), datasetTimezone = Some("GMT+05:30")),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"1701373165123"}"""))
    result1.isValid should be(true)
    result1.value.asInstanceOf[Long] should be(1701392965123L)

    // Validate invalid epoch data in text format (would reset to millis from 1970-01-01 if not epoch in millis)
    val result2 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), Some("epoch")), datasetTimezone = Some("GMT+05:30")),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"170137316512"}"""))
    result2.isValid should be(true)
    result2.value.asInstanceOf[Long] should be(170157116512L)

    // Validate date parser without timezone
    val result3 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), Some("yyyy-MM-dd")), datasetTimezone = None),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"2023-03-01"}"""))
    result3.isValid should be(true)
    result3.value.asInstanceOf[Long] should be(1677609000000L)

    // Validate date parser with timezone
    val result4 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), Some("yyyy-MM-dd")), datasetTimezone = Some("GMT+05:30")),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"2023-03-01"}"""))
    result4.isValid should be(true)
    result4.value.asInstanceOf[Long] should be(1677628800000L)

    // Validate date parser with date time in nano seconds
    val result5 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), Some("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS")), datasetTimezone = Some("GMT+05:30")),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"2023-03-01T12:45:32.123456789"}"""))
    result5.isValid should be(true)
    result5.value.asInstanceOf[Long] should be(1677674732123L)

    // Validate date parser with data in invalid format
    val result6 = TimestampKeyParser.parseTimestampKey(
      DatasetConfig(
        IndexingConfig(olapStoreEnabled = false, lakehouseEnabled = false, cacheEnabled = false), KeysConfig(Some("id"), None, Some("date"), Some("yyyy-MM-dd'T'HH:mm:ss.SSS")), datasetTimezone = Some("GMT+05:30")),
      JSONUtil.deserialize[mutable.Map[String, AnyRef]]("""{"id":1234, "date":"2023-03-01T12:45:32.123456"}"""))
    result6.isValid should be(false)
    result6.value should be(null)
  }

}