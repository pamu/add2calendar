package models

import java.net.URI

import slick.driver.PostgresDriver.api._
//import slick.driver.MySQLDriver.api._

import scala.concurrent.Future

/**
 * Created by pnagarjuna on 13/08/15.
 */
object DB {
  lazy val uri = new URI(s"""postgres://pdfadgyjhqnnln:zcDy12Sp9maEenok4V_tTgcAc-@ec2-54-225-154-5.compute-1.amazonaws.com:5432/d2it67fp1ug27a""")

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
}
