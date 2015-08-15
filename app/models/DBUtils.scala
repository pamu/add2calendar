package models

import scala.concurrent.Future
import slick.driver.PostgresDriver.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
 * Created by pnagarjuna on 13/08/15.
 */
object DBUtils {
  def insertIfNotExistsUser(user: User) = DB.users.forceInsertQuery {
    val exists = (for (u <- DB.users if u.email === user.email) yield u).exists
    val insert = (user.host, user.email, user.pass, None) <> (User.apply _ tupled, User.unapply)
    for (u <- Query(insert) if !exists) yield u
  }
  def createUser(user: User): Future[Int] = {
    DB.db.run(insertIfNotExistsUser(user))
  }
  def fetchUser(email: String): Future[Option[User]]  = {
    val q = for(user <- DB.users.filter(_.email === email)) yield user
    DB.db.run(q.result).map(_.headOption)
  }
  def refreshTime(email: String): Future[Option[RefreshTime]] = {
    val q = for(user <- DB.users.filter(_.email === email);
                refreshTime <- DB.refreshTimes.filter(_.userId === user.id)) yield refreshTime
    DB.db.run(q.result).map(_.headOption)
  }
  def insertIfNotExistsRT(rtime: RefreshTime) = DB.refreshTimes.forceInsertQuery {
    val exists = (for (rt <- DB.refreshTimes if rt.userId === rtime.userId) yield rt).exists
    val insert = (rtime.accessToken, rtime.refreshToken, rtime.refreshTime, rtime.refreshPeriod, rtime.userId, None) <> (RefreshTime.apply _ tupled, RefreshTime.unapply)
    for (rt <- Query(insert) if !exists) yield rt
  }
  def createRefreshTime(refreshTime: RefreshTime): Future[Int] = DB.db.run(insertIfNotExistsRT(refreshTime))
  def fetchUsers: Future[Seq[(User, RefreshTime)]] = {
    val q = for(user <- DB.users;
                refreshTime <- DB.refreshTimes.filter(_.userId === user.id)) yield (user, refreshTime)
    DB.db.run(q.result)
  }
  def updateRefreshTime(refreshTime: RefreshTime): Future[Int] = {
    val q = for(refreshTime <- DB.refreshTimes.filter(_.id === refreshTime.id)) yield refreshTime
    DB.db.run(q.update(refreshTime))
  }
  def getRefreshTimeWithId(id: Long): Future[RefreshTime] = {
    val q = for(refreshTime <- DB.refreshTimes.filter(_.id === id)) yield refreshTime
    DB.db.run(q.result).map(_.head)
  }
  def getUser(id: Long): Future[User] = {
    val q = for(user <- DB.users.filter(_.id === id)) yield user
    DB.db.run(q.result).map(_.head)
  }
}
