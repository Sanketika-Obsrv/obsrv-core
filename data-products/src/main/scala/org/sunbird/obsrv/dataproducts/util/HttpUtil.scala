package org.sunbird.obsrv.dataproducts.util

import kong.unirest.{HttpResponse, JsonNode, Unirest}
import org.apache.logging.log4j.{LogManager, Logger}
import org.sunbird.obsrv.core.exception.ObsrvException
import org.sunbird.obsrv.core.model.ErrorConstants
import org.sunbird.obsrv.dataproducts.MasterDataProcessorIndexer

import scala.collection.JavaConverters._
import scala.language.postfixOps


class HttpUtil extends Serializable {
  private final val logger: Logger = LogManager.getLogger(classOf[HttpUtil])

  def post(url: String, requestBody: String, headers: Map[String, String] = Map[String, String]("Content-Type" -> "application/json")) {
    try{
      Unirest.post(url).headers(headers.asJava).body(requestBody).asJson()
    }catch{
      case ex: Exception =>
        logger.error("Unirest.post() | FAILED ")
        throw new ObsrvException(ErrorConstants.HTTP_SERVER_ERR)
    }
  }

  def delete(url: String) {
    try{
      Unirest.delete(url).header("Content-Type", "application/json").asJson()
    }catch {
      case ex: Exception =>
        logger.error("Unirest.delete() | FAILED ")
        throw new ObsrvException(ErrorConstants.HTTP_SERVER_ERR)
    }
  }
}
