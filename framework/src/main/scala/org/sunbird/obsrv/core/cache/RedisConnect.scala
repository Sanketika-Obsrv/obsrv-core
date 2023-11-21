package org.sunbird.obsrv.core.cache

import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

class RedisConnect(redisHost: String, redisPort: Int, defaultTimeOut: Int) extends java.io.Serializable {

  private val serialVersionUID = -396824011996012513L

  private val logger = LoggerFactory.getLogger(classOf[RedisConnect])


  private def getConnection(backoffTimeInMillis: Long): Jedis = {
    if (backoffTimeInMillis > 0) try Thread.sleep(backoffTimeInMillis)
      // $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked during interrupt
    catch {
      case e: InterruptedException =>
        logger.error("RedisConnect:getConnection() - Exception", e)
        e.printStackTrace()
    }
    // $COVERAGE-ON$
    logger.info("Obtaining new Redis connection...")
    new Jedis(redisHost, redisPort, defaultTimeOut)
  }


  def getConnection(db: Int, backoffTimeInMillis: Long): Jedis = {
    val jedis: Jedis = getConnection(backoffTimeInMillis)
    jedis.select(db)
    jedis
  }

  def getConnection(db: Int): Jedis = {
    val jedis = getConnection(db, backoffTimeInMillis = 0)
    jedis.select(db)
    jedis
  }

  def getConnection: Jedis = getConnection(db = 0)
}
