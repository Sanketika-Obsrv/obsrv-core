package org.sunbird.obsrv.preprocessor

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.{Dataset, DatasetConfig, RouterConfig}
import org.sunbird.obsrv.preprocessor.fixture.EventFixtures
import org.sunbird.obsrv.preprocessor.task.PipelinePreprocessorConfig
import org.sunbird.obsrv.preprocessor.util.SchemaValidator

class TestSchemaValidator extends FlatSpec with Matchers {

  val config: Config = ConfigFactory.load("test.conf")
  val pipelineProcessorConfig = new PipelinePreprocessorConfig(config)
  val schemaValidator = new SchemaValidator(pipelineProcessorConfig)

  "SchemaValidator" should "return a success report for a valid event" in {

    val dataset = Dataset("d1", "dataset", None, None, None, Option(EventFixtures.VALID_SCHEMA), None, RouterConfig(""), DatasetConfig("id","date","ingest"), "Active")
    schemaValidator.loadDataSchemas(List(dataset))

    val event = JSONUtil.deserialize[Map[String, AnyRef]](EventFixtures.VALID_SCHEMA_EVENT)
    val report = schemaValidator.validate("d1", event)
    assert(report.isSuccess)
  }

  it should "return a failed validation report for a invalid event" in {

    val dataset = Dataset("d1", "dataset", None, None, None, Option(EventFixtures.VALID_SCHEMA), None, RouterConfig(""), DatasetConfig("id","date","ingest"), "Active")
    schemaValidator.loadDataSchemas(List(dataset))

    val event = JSONUtil.deserialize[Map[String, AnyRef]](EventFixtures.INVALID_SCHEMA_EVENT)
    val report = schemaValidator.validate("d1", event)
    assert(!report.isSuccess)
    assert(report.toString.contains("error: object has missing required properties ([\"vehicleCode\"])"))

    val invalidFieldName = schemaValidator.getInvalidFieldName(report.toString)
    invalidFieldName should be ("Unable to obtain field name for failed validation")
  }

  it should "validate the negative scenarios" in {
    val dataset = Dataset("d1", "dataset", None, None, None, Option(EventFixtures.INVALID_SCHEMA), None, RouterConfig(""), DatasetConfig("id","date","ingest"), "Active")
    schemaValidator.loadDataSchemas(List(dataset))

    val dataset2 = Dataset("d1", "dataset", None, None, None, None, None, RouterConfig(""), DatasetConfig("id","date","ingest"), "Active")
    an[ObsrvException] should be thrownBy schemaValidator.schemaFileExists(dataset2)
    schemaValidator.schemaFileExists(dataset) should be (false)
  }

}
