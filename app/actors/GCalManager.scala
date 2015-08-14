package actors

import java.sql.Timestamp
import java.util.Date
import javax.mail.Message

import akka.actor.{Actor, ActorLogging}
import constants.{Urls, Constants}
import controllers.Application._
import controllers.routes
import models.{DBUtils, RefreshTime}
import play.api.libs.json.Json
import utils.WS

import play.api.libs.concurrent.Execution.Implicits.defaultContext

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
      val millis = System.currentTimeMillis() - refreshTime.refreshTime.getTime
      if ((millis/1000000) < (refreshTime.refreshPeriod - 60)) {

      } else {

      }
    case StopGCalManager =>
    case _ =>
  }
}

object CalUtils {
  def refresh(refreshToken: String, id: Long): Unit = {
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
        DBUtils.updateRefreshTime(refreshTime).map {
          id => {
            if (id > 0) {
              Redirect(routes.Application.status()).flashing("success" -> "Done")
            } else {
              Redirect(routes.Application.home()).flashing("failure" -> "problem storing refresh time")
            }
          }
        }.recover {case th => Redirect(routes.Application.home()).flashing("failure" -> "problem storing refresh time")}
      }
    }.recover { case th => Ok(s"failed ${th.getMessage}")}
  }
}