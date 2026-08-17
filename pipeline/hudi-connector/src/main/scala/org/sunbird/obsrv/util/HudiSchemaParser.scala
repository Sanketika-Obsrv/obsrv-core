package org.sunbird.obsrv.util

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.JsonGenerator.Feature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.flink.table.types.logical.{BigIntType, BooleanType, DoubleType, IntType, LogicalType, MapType, RowType, VarCharType, TimestampType, DateType}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.Constants
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.registry.DatasetRegistry
import java.sql.Timestamp
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.collection.mutable


case class HudiSchemaSpec(dataset: String, schema: Schema, inputFormat: InputFormat)
case class Schema(table: String, partitionColumn: String, timestampColumn: String, primaryKey: String, columnSpec: List[ColumnSpec])
case class ColumnSpec(name: String, `type`: String)
case class InputFormat(`type`: String, flattenSpec: Option[JsonFlattenSpec] = None, columns: Option[List[String]] = None)
case class JsonFlattenSpec(fields: List[JsonFieldParserSpec])
case class JsonFieldParserSpec(`type`: String, name: String, expr: Option[String] = None)

class HudiSchemaParser {

  private val logger = LoggerFactory.getLogger(classOf[HudiSchemaParser])

  @transient private val objectMapper = JsonMapper.builder()
    .addModule(DefaultScalaModule)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .enable(Feature.WRITE_BIGDECIMAL_AS_PLAIN)
    .build()

  // DateTimeFormatter over SimpleDateFormat: SimpleDateFormat isn't thread-safe, and this field
  // is shared across every parseJson(...) call on this instance.
  val df: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  objectMapper.setSerializationInclusion(Include.NON_ABSENT)

  val hudiSchemaMap = new mutable.HashMap[String, HudiSchemaSpec]()
  val rowTypeMap = new mutable.HashMap[String, RowType]()

  readSchema()

  def readSchema(): Unit = {
    val datasourceConfig = DatasetRegistry.getAllDatasources().filter(f => f.`type`.nonEmpty && f.`type`.equalsIgnoreCase(Constants.DATALAKE_TYPE) && f.status.equalsIgnoreCase("Live"))
    datasourceConfig.map{f =>
      val hudiSchemaSpec = JSONUtil.deserialize[HudiSchemaSpec](f.ingestionSpec)
      val dataset = hudiSchemaSpec.dataset
      hudiSchemaMap.put(dataset, hudiSchemaSpec)
      rowTypeMap.put(dataset, createRowType(hudiSchemaSpec))
    }
  }

  private def createRowType(schema: HudiSchemaSpec): RowType = {
    val columnSpec = schema.schema.columnSpec
    val primaryKey = schema.schema.primaryKey
    val partitionColumn = schema.schema.partitionColumn
    val timeStampColumn = schema.schema.timestampColumn
    val partitionField = schema.schema.columnSpec.find(f => f.name.equalsIgnoreCase(schema.schema.partitionColumn))
      .getOrElse(throw new IllegalArgumentException(s"partitionColumn '${schema.schema.partitionColumn}' not found in columnSpec for dataset '${schema.dataset}'"))
    val rowTypeMap = mutable.SortedMap[String, LogicalType]()
    columnSpec.sortBy(_.name).map {
      spec =>
        // Was spec.name.matches(s"$primaryKey|$partitionColumn|$timeStampColumn") - primaryKey/
        // partitionColumn/timeStampColumn get interpolated straight into a regex, unescaped, so
        // a column name containing regex metacharacters (e.g. a dataset field literally named
        // "a.b" - "." meaning "any character") could false-match unrelated names. Explicit Set
        // containment sidesteps regex entirely.
        val nonNullableFields = Set(primaryKey, partitionColumn, timeStampColumn).filter(_.nonEmpty)
        val isNullable = !nonNullableFields.contains(spec.name)
        val columnType = spec.`type` match {
          // Was VarCharType(isNullable, 20) - hard-capped every string column (email, URLs,
          // arbitrary payload content, UUIDs) to 20 chars, silently truncating or failing schema
          // conversion for anything longer.
          case "string" => new VarCharType(isNullable, VarCharType.MAX_LENGTH)
          case "double" => new DoubleType(isNullable)
          case "long" => new BigIntType(isNullable)
          case "int" => new IntType(isNullable)
          case "boolean" => new BooleanType(true)
          case "map[string, string]" => new MapType(new VarCharType(), new VarCharType())
          case "epoch" => new BigIntType(isNullable)
          case _ => new VarCharType(isNullable, VarCharType.MAX_LENGTH)
        }
        rowTypeMap.put(spec.name, columnType)
    }
    if(partitionField.`type`.equalsIgnoreCase("timestamp") || partitionField.`type`.equalsIgnoreCase("epoch")) {
      rowTypeMap.put(partitionField.name + "_partition", new VarCharType(false, 20))
    }
    val rowType: RowType = RowType.of(false, rowTypeMap.values.toArray, rowTypeMap.keySet.toArray)
    logger.info("rowType: " + rowType)
    rowType
  }

  def parseJson(dataset: String, event: String): mutable.Map[String, Any] = {
    val parserSpec = hudiSchemaMap.get(dataset)
    val jsonNode = objectMapper.readTree(event)
    val flattenedEventData = mutable.Map[String, Any]()
    parserSpec.map { spec =>
      val columnSpec = spec.schema.columnSpec
      val partitionField = spec.schema.columnSpec.find(f => f.name.equalsIgnoreCase(spec.schema.partitionColumn))
        .getOrElse(throw new IllegalArgumentException(s"partitionColumn '${spec.schema.partitionColumn}' not found in columnSpec for dataset '$dataset'"))
      spec.inputFormat.flattenSpec.map {
        flattenSpec =>
          flattenSpec.fields.map {
            field =>
              val node = retrieveFieldFromJson(jsonNode, field)
              node.map {
                nodeValue =>
                  try {
                    val fieldDataType = columnSpec.find(_.name.equalsIgnoreCase(field.name))
                      .getOrElse(throw new IllegalArgumentException(s"field '${field.name}' not found in columnSpec for dataset '$dataset'")).`type`
                    val fieldValue = fieldDataType match {
                      case "string" => objectMapper.treeToValue(nodeValue, classOf[String])
                      case "int" => objectMapper.treeToValue(nodeValue, classOf[Int])
                      case "long" => objectMapper.treeToValue(nodeValue, classOf[Long])
                      case "double" => objectMapper.treeToValue(nodeValue, classOf[Double])
                      case "epoch" => objectMapper.treeToValue(nodeValue, classOf[Long])
                      case _ => objectMapper.treeToValue(nodeValue, classOf[String])
                    }
                    if(field.name.equalsIgnoreCase(partitionField.name)){
                      if(fieldDataType.equalsIgnoreCase("timestamp")) {
                        // Verified empirically (jackson-databind:2.17.2, the version this project
                        // pins): objectMapper.treeToValue(nodeValue, classOf[Timestamp]) already
                        // handles numeric epoch-millis AND ISO-8601 strings - 'Z'-terminated,
                        // explicit-offset ("+05:30"), zone-less, with or without fractional
                        // seconds - all parse fine via Jackson's own lenient StdDateFormat. The
                        // one real gap: java.sql.Timestamp's OWN string format
                        // ("yyyy-MM-dd HH:mm:ss[.f...]", space instead of 'T') throws
                        // InvalidFormatException - if that aborts here, "_partition" (schema'd
                        // non-nullable) never gets set and NPEs downstream in
                        // JsonToRowDataConverter. Falls back to Timestamp.valueOf (the JDK's own
                        // parser for exactly that format) for that one case.
                        // Also (independent of parsing format) resolves to a fixed UTC zone, not
                        // the JVM's default - different TaskManagers can have different default
                        // zones, placing the same instant in different Hudi partitions depending
                        // on which TM processed it.
                        val ts = try {
                          objectMapper.treeToValue(nodeValue, classOf[Timestamp])
                        } catch {
                          case _: Exception if nodeValue.isTextual => Timestamp.valueOf(nodeValue.asText())
                        }
                        val localDateTime = LocalDateTime.ofInstant(ts.toInstant, ZoneOffset.UTC)
                        flattenedEventData.put(field.name + "_partition", localDateTime.format(df))
                      }
                      else if(fieldDataType.equalsIgnoreCase("epoch")) {
                        // Was df.format(Long) with a SimpleDateFormat - format(Object) expects a
                        // Date, so passing a boxed Long here would have thrown
                        // IllegalArgumentException at runtime, not silently misformatted.
                        val epochMillis = objectMapper.treeToValue(nodeValue, classOf[Long])
                        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                        flattenedEventData.put(field.name + "_partition", localDateTime.format(df))
                      }
                    }
                    flattenedEventData.put(field.name, fieldValue)
                  }
                  catch {
                    case ex: Exception =>
                      // logger.debug("Hudi Schema Parser - Exception: ", ex.getMessage)
                      flattenedEventData.put(field.name, null)
                  }

              }.orElse(flattenedEventData.put(field.name, null))
          }
      }
    }
    // logger.debug("flattenedEventData: " + flattenedEventData)
    flattenedEventData
  }

  def retrieveFieldFromJson(jsonNode: JsonNode, field: JsonFieldParserSpec): Option[JsonNode] = {
    // jsonNode.at(...) returns MissingNode (a real, non-null JsonNode), not Java null, for a
    // path that doesn't resolve - Option(missingNode) evaluated to Some(missingNode), so a
    // genuinely-absent path-type field looked "found" here and blew up later in
    // objectMapper.treeToValue(missingNode, ...), silently swallowed by the catch in parseJson.
    // Was `f.split("\\.").tail.mkString("/")` - unconditionally dropped the FIRST dotted
    // segment. That's correct for the "$.foo.bar" JSONPath-style convention this connector's
    // own shipped example (schemas/schema.json) actually uses ("$" is a root indicator meant to
    // be dropped, "$.sender.account_number" -> "/sender/account_number", verified correct), but
    // wrong for any expr that doesn't start with that "$." prefix: "actor.id" would drop "actor"
    // and look up top-level "/id" instead of nested "/actor/id", and a single-segment expr with
    // no dot at all (e.g. "id") would drop its only segment and look up "/" instead of "/id".
    // Only strip a leading "$" segment specifically (the documented convention), otherwise use
    // every segment - handles both the "$."-prefixed convention and a bare dotted/single path.
    val node = if (field.`type`.equalsIgnoreCase("path")) {
      field.expr.map { f =>
        val segments = f.split("\\.")
        val pathSegments = if (segments.nonEmpty && segments.head == "$") segments.tail else segments
        jsonNode.at("/" + pathSegments.mkString("/"))
      }.orNull
    } else {
      jsonNode.get(field.name)
    }
    if (node != null && !node.isMissingNode && !node.isNull) Some(node) else None
  }
}
