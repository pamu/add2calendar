package models

import java.net.URI

import slick.driver.PostgresDriver.api._
//import slick.driver.MySQLDriver.api._

import scala.concurrent.Future

/**
 * Created by pnagarjuna on 13/08/15.
 */
object DB {
  lazy val uri = new URI(s"""postgres://tddesciitsxjax:YatfmAmmHecftO5GZllgC5FOeA@ec2-54-217-202-110.eu-west-1.compute.amazonaws.com:5432/deie2osv6r8d9p""")

  lazy val username = uri.getUserInfo.split(":")(0)

  lazy val password = uri.getUserInfo.split(":")(1)

  lazy val db = Database.forURL(
     driver = "org.postgresql.Driver",
     url = "jdbc:postgresql://" + uri.getHost + ":" + uri.getPort + uri.getPath, user = username,
     password = password
    )

  /**
  lazy val db = Database.forURL(
    url = s"jdbc:mysql://localhost/add2cal",
    driver = "com.mysql.jdbc.Driver",
    user="root",
    password="root") **/

  lazy val users = TableQuery[Users]
  lazy val refreshTimes =TableQuery[RefreshTimes]

  def init: Future[Unit] = {
    db.run(DBIO.seq((users.schema ++ refreshTimes.schema).create))
  }

  def clean: Future[Unit] = {
    db.run(DBIO.seq(refreshTimes.schema.drop, users.schema.drop))
  }
}
