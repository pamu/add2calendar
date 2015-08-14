package actors

import java.sql.Timestamp
import java.util.Date
import javax.mail.Message
import javax.mail.internet.MimeMultipart

import akka.actor.{Status, Actor, ActorLogging}
import constants.{Urls, Constants}
import controllers.Application._
import models.{DBUtils, RefreshTime}
import play.api.Logger
import play.api.libs.json.{JsNull, Json}
import utils.WS

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

import akka.pattern.pipe

import scala.util.Try

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

      val lines = CalUtils.getBody(msg).map {
        body => {
          val lines = body.split("\n").map(_.trim)
          if (lines.length > 1) s"${lines(0)} ${lines(1)}" else lines(0)
        }
      }

      DBUtils.getRefreshTimeWithId(refreshTime.id.get).flatMap {
        rt => {
          val current = System.currentTimeMillis / 1000
          val last= rt.refreshTime.getTime / 1000
          val tolerance = 60
          if ((current - last) < (3600 - tolerance)) {
            CalUtils.createQuickEvent(rt.accessToken, msg.getSubject, lines)
          } else {
            CalUtils.refresh(refreshTime.refreshToken, rt.userId, rt.id).flatMap {
              id => {
                DBUtils.getRefreshTimeWithId(rt.id.get).map {
                  y => CalUtils.createQuickEvent(y.accessToken, msg.getSubject, lines)
                }
              }
            }
          }
        }
      } pipeTo self
    case Status.Failure(th) =>
      log info s"failure in gcal manager ${th.getMessage}"
      th.printStackTrace()
    case status: Int => log info s"success status: $status"
    case unit: Unit => log info "success unit returned"
    case StopGCalManager => context stop self
    case x => {
      log info s"unknown message in GCalManager ${x.getClass}"
    }
  }
}

object CalUtils {

  def refresh(refreshToken: String, userId: Long, id: Option[Long]): Future[Int] = {

    val body = Map[String, String](
      ("client_id" -> Constants.client_id),
      ("client_secret" -> Constants.client_secret),
      ("refresh_token" -> refreshToken),
      ("grant_type" -> "refresh_token")
    )

    Logger info s"$refreshToken token"

    WS.client.url(Urls.TokenEndpoint)
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8")
      .post(body.convert.mkString("", "&", "")).flatMap {
      response => {
        val tokens = Json.parse(response.body)
        Logger info s"json parsed $tokens"
        val refreshTime = RefreshTime((tokens \ "access_token").asOpt[String].get, refreshToken, new Timestamp(new Date().getTime), (tokens \ "expires_in").asOpt[Long].get, userId, id)
        DBUtils.updateRefreshTime(refreshTime)
        }
      }

    }

  def createQuickEvent(access_token: String, subject: String, body: Option[String] = None): Future[Int] = {

    val request = WS.client.url(Urls.Calendar.calendarQuickAdd("primary")).withQueryString(
      ("access_token" -> access_token),
      ("text" -> body.getOrElse(subject).toString),
      ("sendNotifications" -> "true")
    )

    val payload = Json.obj(
      ("summary" -> subject),
      ("description" -> body.getOrElse(subject).toString)
    )

    val response = request.post(payload)

    response.flatMap {
      res => {
        Logger info "quick event creation"
        Logger info s"${res.body.toString}"
        if (res.status == 200) {
          Logger info "event creation successful"
          val jsonRes = Json.parse(res.body)
          val quickCalEvent = QuickCalEvent(
            (jsonRes \ "id").as[String],
            ((jsonRes \ "start") \ "dateTime").as[String],
            ((jsonRes \ "end") \ "dateTime").as[String],
            ((jsonRes \ "organizer") \ "email").as[String],
            (jsonRes \ "location").asOpt[String]
          )
          updateEvent(access_token, subject, body.getOrElse(subject), quickCalEvent)
        } else {
          case class EventCreationFailed(msg: String, status: Int) extends Exception(msg)
          throw new EventCreationFailed(res.body.toString, res.status)
        }
      }
    }

  }

  case class QuickCalEvent(eventId: String,
                           startDateTime: String,
                           endDateTime: String,
                            organizerEmail: String,
                           location: Option[String] = None)

  def updateEvent(access_token: String, subject: String, body: String, quickCalEvent: QuickCalEvent): Future[Int] = {
    val request = WS.client.url(Urls.Calendar.calendarUpdate("primary", quickCalEvent.eventId)).withQueryString(
      ("access_token" -> access_token),
      ("sendNotifications" -> "true")
    )
    val data = quickCalEvent.location.map {
      loc => {
        Json.obj(
          "summary" -> subject,
          "description" -> body,
          "location" -> loc,
          "attachments" -> Json.obj("fileUrl" -> ""),
          "attendees" -> Json.arr(Json.obj("email" -> quickCalEvent.organizerEmail)),
          "start" -> Json.obj("date" -> JsNull, "dateTime" -> quickCalEvent.startDateTime),
          "end" -> Json.obj("date" -> JsNull, "dateTime" -> quickCalEvent.endDateTime),
          "reminders" -> Json.obj("useDefault" -> false, "overrides" -> Json.arr(Json.obj("method" -> "email", "minutes" -> "5")))
        )
      }
    }.getOrElse {
      Json.obj(
        "summary" -> subject,
        "description" -> body,
        "attachments" -> Json.obj("fileUrl" -> ""),
        "attendees" -> Json.arr(Json.obj("email" -> quickCalEvent.organizerEmail)),
        "start" -> Json.obj("date" -> JsNull, "dateTime" -> quickCalEvent.startDateTime),
        "end" -> Json.obj("date" -> JsNull, "dateTime" -> quickCalEvent.endDateTime),
        "reminders" -> Json.obj("useDefault" -> false, "overrides" -> Json.arr(Json.obj("method" -> "email", "minutes" -> "5")))
      )
    }

    request.put(data).map {
      response => {
        Logger info s"update event response ${response.body.toString}"
        if (response.status == 200) {
          Logger info "update event successful"
          response.status
        } else {
          case class EventUpdateFailed(msg: String, status: Int) extends Exception(msg)
          throw new EventUpdateFailed("Event update failed", response.status)
        }
      }
    }
  }

  def getBody(msg: Message): Option[String] = {
    val body: Try[String] = Try {
      val mimeMultiPart = msg.getContent.asInstanceOf[MimeMultipart]
      val bodyParts = for( i <- 0 until mimeMultiPart.getCount) yield mimeMultiPart.getBodyPart(i)
      val str = bodyParts.filter(_.isMimeType("text/plain")).head.getContent.toString
      str
    }
    body.toOption
  }

}