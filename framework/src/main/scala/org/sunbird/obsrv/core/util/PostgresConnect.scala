package org.sunbird.obsrv.core.util

import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory

import java.sql.{Connection, PreparedStatement, ResultSet, SQLException, Statement}

final case class PostgresConnectionConfig(user: String, password: String, database: String, host: String, port: Int, maxConnections: Int)

class PostgresConnect(config: PostgresConnectionConfig) {

  private[this] val logger = LoggerFactory.getLogger(classOf[PostgresConnect])

  private var source: PGSimpleDataSource = null

  buildPoolConfig()

  private var connection: Connection = source.getConnection
  private var statement: Statement = connection.createStatement

  @throws[Exception]
  private def buildPoolConfig(): Unit = {
    Class.forName("org.postgresql.Driver")
    source = new PGSimpleDataSource()
    source.setServerNames(Array(config.host))
    source.setPortNumbers(Array(config.port))
    source.setUser(config.user)
    source.setPassword(config.password)
    source.setDatabaseName(config.database)
  }

  @throws[Exception]
  def reset() = {
    closeConnection()
    buildPoolConfig()
    connection = source.getConnection
    statement = connection.createStatement
  }

  @throws[Exception]
  def closeConnection(): Unit = {
    connection.close()
  }

  @throws[Exception]
  def execute(query: String): Boolean = {
    try {
      statement.execute(query)
    }
    // $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked if postgres connection is stale
    catch {
      case ex: SQLException =>
        logger.error("PostgresConnect:execute() - Exception", ex)
        reset()
        statement.execute(query)
    }
    // $COVERAGE-ON$
  }

  def executeUpdate(query: String): Int = {
    try {
      statement.executeUpdate(query)
    }
      // $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked if postgres connection is stale
    catch {
      case ex: SQLException =>
        logger.error("PostgresConnect:execute() - Exception", ex)
        reset()
        statement.executeUpdate(query)
    }
    // $COVERAGE-ON$
  }

  def prepareStatement(query: String): PreparedStatement = {
    try {
      connection.prepareStatement(query)
    } catch {
      case ex: SQLException =>
        ex.printStackTrace()
        logger.error("PostgresConnect:prepareStatement() - Exception", ex)
        reset()
        connection.prepareStatement(query)
    }
  }

  def executeUpdate(preparedStatement: PreparedStatement): Int = {
    try {
      preparedStatement.executeUpdate()
    } catch {
      case ex: SQLException =>
        ex.printStackTrace()
        logger.error("PostgresConnect:executeUpdate():PreparedStatement - Exception", ex)
        reset()
        preparedStatement.executeUpdate()
    }
  }

  def executeQuery(preparedStatement: PreparedStatement): ResultSet = {
    try {
      preparedStatement.executeQuery()
    } catch {
      case ex: SQLException =>
        logger.error("PostgresConnect:execute():PreparedStatement - Exception", ex)
        reset()
        preparedStatement.executeQuery()
    }
  }

  def executeQuery(query:String):ResultSet = statement.executeQuery(query)
}


