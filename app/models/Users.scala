package models

import slick.driver.PostgresDriver.api._
//import slick.driver.MySQLDriver.api._
/**
 * Created by pnagarjuna on 13/08/15.
 */
class Users(tag: Tag) extends Table[User](tag, "users"){
  def host = column[String]("host")
  def email = column[String]("email")
  def pass = column[String]("pass")
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def * = (host, email, pass, id.?) <> (User.tupled, User.unapply)
}
