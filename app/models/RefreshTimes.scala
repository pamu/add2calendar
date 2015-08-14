package models

import java.sql.Timestamp

import slick.driver.PostgresDriver.api._
//import slick.driver.MySQLDriver.api._
/**
 * Created by pnagarjuna on 13/08/15.
 */
class RefreshTimes(tag: Tag) extends Table[RefreshTime](tag, "refresh_times"){
  def accessToken = column[String]("access_token")
  def refreshToken = column[String]("refresh_token")
  def refreshTime = column[Timestamp]("refresh_time")
  def refreshPeriod = column[Long]("refresh_period")
  def userId = column[Long]("user_id")
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def * = (accessToken, refreshToken, refreshTime, refreshPeriod, userId, id.?) <> (RefreshTime.tupled, RefreshTime.unapply)
  def userIdFk = foreignKey("user_id_refresh_time_fk", userId, TableQuery[Users])(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)
}
