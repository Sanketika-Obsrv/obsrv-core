package org.sunbird.obsrv.dataproducts.exception

case class DispatcherException(msg: String, ex: Exception = null) extends Exception(msg, ex) {}
