package org.sunbird.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.junit.Assert.{assertEquals, assertNotNull}
import org.scalatest.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.sunbird.obsrv.core.util.{PostgresConnect, PostgresConnectionConfig}


class PostgresConnectSpec extends BaseSpecWithPostgres with Matchers with MockitoSugar {

  val config: Config = ConfigFactory.load("base-test.conf")

  "PostgresConnection" should "able to connect and query the postgres" in {

    val postgresConfig = PostgresConnectionConfig(
      user = config.getString("postgres.user"),
      password = config.getString("postgres.password"),
      database = "postgres",
      host = config.getString("postgres.host"),
      port = config.getInt("postgres.port"),
      maxConnections = config.getInt("postgres.maxConnections")
    )
    val postgresConnect = new PostgresConnect(postgresConfig)

    postgresConnect.execute("CREATE TABLE device_table(id text PRIMARY KEY, channel text);")
    postgresConnect.execute("INSERT INTO device_table(id,channel)  VALUES('12345','custchannel');")

    val rs = postgresConnect.executeQuery("SELECT * FROM device_table where id='12345';")
    while ( {
      rs.next
    }) {
      assertEquals("12345", rs.getString("id"))
      assertEquals("custchannel", rs.getString("channel"))
    }

    val resetConnection: Unit = postgresConnect.reset()
    assertNotNull(resetConnection)
    postgresConnect.closeConnection()
  }

}
