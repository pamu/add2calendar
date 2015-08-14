package actors

import java.io.{StringBufferInputStream, ByteArrayInputStream}
import java.sql.Timestamp
import java.util.Date
import javax.mail.Message

import akka.actor.{Status, Actor, ActorLogging}
import constants.{Urls, Constants}
import controllers.Application._
import models.{DBUtils, RefreshTime}
import play.api.libs.json.Json
import utils.WS

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

import akka.pattern.pipe

/**
 * Created by pnagarjuna on 14/08/15.
 */
object GCalManager {
  case object StopGCalManager
  case class CreateEvent(msg: Message)
}

class GCalManager(refreshTime: RefreshTime) extends Actor with ActorLogging {

  import GCalManager._


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    log info s"started a gcal manager for userId: ${refreshTime.userId}, refresh id: ${refreshTime.id}"
  }

  def receive = {
    case CreateEvent(msg) =>
      log info "create info called " + msg.getSubject + " "
      DBUtils.getRefreshTimeWithId(refreshTime.id.get).flatMap {
        rtOption => rtOption.map {
          rt => {
            val current = System.currentTimeMillis / 1000000
            val last= rt.refreshTime.getTime / 1000000
            val tolerance = 60
            if ((current - last) < (3600 - tolerance)) {
              CalUtils.createQuickEvent(rt.accessToken, msg.getSubject, msg.getSubject)
            } else {
              CalUtils.refresh(rt.refreshToken, rt.id.get).flatMap {
               id => {
                 DBUtils.getRefreshTimeWithId(rt.id.get).map {
                   x => x.map {
                     y => CalUtils.createQuickEvent(y.accessToken, msg.getSubject, msg.getSubject)
                   }.getOrElse(Future(Unit))
                 }
               }
              }
            }
          }
        }.getOrElse(Future(Unit))
      } pipeTo self
    case Status.Failure(th) =>
      log info s"failure in gcal manager ${th.getMessage}"
      th.printStackTrace()
    case StopGCalManager => context stop self
    case x => log info s"unknown message in GCalManager ${x.getClass}"
  }
}

object CalUtils {

  def refresh(refreshToken: String, id: Long): Future[Int] = {

    val body = Map[String, String](
      ("client_id" -> Constants.client_id),
      ("client_secret" -> Constants.client_secret),
      ("refresh_token" -> refreshToken),
      ("grant_type" -> "refresh_token")
    )

    WS.client.url(Urls.TokenEndpoint)
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8")
      .post(body.convert.mkString("", "&", "")).flatMap {
      response => {
        val tokens =Json.parse(response.body)
        val refreshTime = RefreshTime((tokens \ "access_token").asOpt[String].get, (tokens \ "refresh_token").asOpt[String].get, new Timestamp(new Date().getTime), (tokens \ "expires_in").asOpt[Long].get, id)
        DBUtils.updateRefreshTime(refreshTime)
        }
      }

    }

  def createQuickEvent(access_token: String, subject: String, body: String): Future[Int] = {

    val request = WS.client.url(Urls.Calendar.calendarQuickAdd("primary")).withQueryString(
      ("access_token" -> access_token),
      ("text" -> body),
      ("sendNotifications" -> "true")
    )

    val payload = Json.obj(
      ("summary" -> subject),
      ("description" -> body)
    )

    val response = request.post(payload)

    response.map {
      res => {
        if (res.status == 200) {
          res.status
        } else {
          case class EventCreationFailed(msg: String, status: Int) extends Exception(msg)
          throw new EventCreationFailed(res.body.toString, res.status)
        }
      }
    }

  }


}