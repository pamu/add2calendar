package models

import scala.concurrent.Future
import slick.driver.PostgresDriver.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
 * Created by pnagarjuna on 13/08/15.
 */
object DBUtils {
  def insertIfNotExistsUser(user: User) = (
      DB.users.filter(_.email === user.email).exists.result.flatMap { exists =>
        if (!exists) {
          DB.users += user
        } else {
          DBIO.successful(1)
        }
      }
    ).transactionally
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
  def insertIfNotExistsRT(rtime: RefreshTime) = (
      DB.refreshTimes.filter(_.userId === rtime.userId).exists.result.flatMap { exists =>
        if (!exists) {
          DB.refreshTimes += rtime
        } else {
          DB.refreshTimes.filter(_.userId === rtime.userId).result.flatMap { rt =>
            DB.refreshTimes.filter(_.userId === rtime.userId).update(rtime.copy(id = rt.head.id))
          }
        }
      }
    ).transactionally
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
