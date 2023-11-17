package org.sunbird.obsrv.dataproducts.exception

case class InvalidInputException(msg: String, ex: Exception = null) extends Exception(msg, ex) {
  //print the error message
  println(msg)
}
