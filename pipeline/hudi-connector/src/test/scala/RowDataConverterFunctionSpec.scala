// package org.sunbird.obsrv.function

// import org.scalatest.FlatSpec
// import org.scalatest.Matchers
// import org.apache.flink.table.data.RowData
// import org.sunbird.obsrv.streaming.HudiConnectorConfig
// import org.sunbird.obsrv.function.RowDataConverterFunction
// import scala.collection.mutable.{Map => MMap}

// object EventFixtures {
//   val VALID_EVENT: MMap[String, AnyRef] = MMap("id" -> "123", "name" -> "test", "timestamp" -> "2025-07-17T12:00:00Z")
//   val INVALID_EVENT: MMap[String, AnyRef] = MMap("id" -> 123, "name" -> null) // id should be string, name should not be null
// }

// class RowDataConverterFunctionSpec extends FlatSpec with Matchers {

//   val config = HudiConnectorConfig(jobName = "testJob", inputEventCountMetric = "inputCount", failedEventCountMetric = "failedCount")
//   val datasetId = "testDataset"

//   def setupFunctionWithSchema(): RowDataConverterFunction = {
//     val func = new RowDataConverterFunction(config, datasetId)
//     func.open(null)
//     class TestHudiSchemaParser extends org.sunbird.obsrv.util.HudiSchemaParser {
//       override def parseJson(datasetId: String, eventJson: String): Map[String, AnyRef] = {
//         import org.sunbird.obsrv.core.util.JSONUtil
//         JSONUtil.deserialize[Map[String, AnyRef]](eventJson)
//       }
//     }
//     val testParser = new TestHudiSchemaParser()
//     import org.apache.flink.table.types.logical.{RowType, VarCharType}
//     val rowType = new RowType(java.util.Arrays.asList(
//       new RowType.RowField("id", new VarCharType()),
//       new RowType.RowField("name", new VarCharType()),
//       new RowType.RowField("timestamp", new VarCharType())
//     ))
//     testParser.rowTypeMap.put(datasetId, rowType)
//     testParser.schemaMap.put(datasetId, Map("id" -> "string", "name" -> "string", "timestamp" -> "string"))
//     val hudiSchemaParserField = func.getClass.getDeclaredField("hudiSchemaParser")
//     hudiSchemaParserField.setAccessible(true)
//     hudiSchemaParserField.set(func, testParser)
//     func
//   }

//   "RowDataConverterFunction" should "convert valid event map to RowData" in {
//     val func = setupFunctionWithSchema()
//     val rowData: RowData = func.map(EventFixtures.VALID_EVENT)
//     rowData should not be (null)
//   }

//   it should "throw exception for invalid event map" in {
//     val func = setupFunctionWithSchema()
//     intercept[Exception] {
//       func.map(EventFixtures.INVALID_EVENT)
//     }
//   }
// }
