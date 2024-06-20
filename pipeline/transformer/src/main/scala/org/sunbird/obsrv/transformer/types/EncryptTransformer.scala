package org.sunbird.obsrv.transformer.types

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import org.sunbird.obsrv.transformer.functions.TransformerFunctionHelper.JsonHelper
import org.sunbird.obsrv.transformer.util.CipherUtil
import org.json4s.{JValue, MappingException}
import org.slf4j.LoggerFactory
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.core.util.JSONUtil
import org.sunbird.obsrv.model.DatasetModels.DatasetTransformation

class EncryptTransformer extends ITransformer {

  private val logger = LoggerFactory.getLogger(classOf[EncryptTransformer])

  override def transformField(json: JValue, jsonNode: JsonNode, dt: DatasetTransformation): (JValue, TransformFieldStatus) = {
    val emptyNode = getJSON(dt.fieldKey, MissingNode.getInstance())
    try {
      val currentValue = json.customExtract[String](dt.transformationFunction.expr)
      val encryptedValue = CipherUtil.encrypt(currentValue)
      (getJSON(dt.fieldKey, encryptedValue), TransformFieldStatus(dt.fieldKey, dt.transformationFunction.expr, success = true, dt.mode.get))
    } catch {
      case ex: MappingException =>
        logger.error(s"Transformer(Encrypt) | Exception parsing transformation expression | Data=${JSONUtil.serialize(dt)} | error=${ex.getMessage}", ex)
        (emptyNode, TransformFieldStatus(dt.fieldKey, dt.transformationFunction.expr, success = false, dt.mode.get, Some(ErrorConstants.TRANSFORMATION_FIELD_MISSING)))
    }
  }

}

object EncryptTransformer {

  private val encryptTransformer = new EncryptTransformer()

  def transform(json: JValue, jsonNode: JsonNode, dtList: List[DatasetTransformation]): TransformationResult = {
    encryptTransformer.transform(json, jsonNode, dtList)
  }

}