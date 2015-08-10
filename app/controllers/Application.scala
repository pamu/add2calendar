package controllers

import constants.{Constants, Urls}
import play.api.Logger
import play.api.libs.json.{JsValue, JsNull, Json}
import play.api.mvc.{Action, Controller}
import utils.WS

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }

  implicit class MapConverter(rMap: Map[String, String]) {
    def convert: List[String] = rMap.map(pair => s"${pair._1}=${pair._2}").toList
  }

  def oauth2 = Action {
    val params = Map[String, String](
      ("scope" -> "https://www.googleapis.com/auth/calendar"),
      ("state" -> "scala"),
      ("response_type" -> "code"),
      ("client_id" -> s"${Constants.client_id}"),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("access_type" -> "offline"),
      ("approval_prompt" -> "force")
    ).convert.mkString("?", "&", "").toString

    val requestURI = s"${Urls.GoogleOauth2}${params}"

    Redirect(requestURI)
  }

  def oauth2callback(state: Option[String], code: Option[String], error: Option[String]) = Action {
    code match {
      case Some(code) => Redirect(routes.Application.onCode(code))
      case None => {
        error match {
          case Some(err) => Ok("Error")
          case None => Ok("No Error")
        }
      }
    }
  }

  def onCode(code: String) = Action.async {
    val body = Map[String, String](
      ("code" -> s"$code"),
      ("client_id" -> s"${Constants.client_id}"),
      ("client_secret" -> s"${Constants.client_secret}"),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("grant_type" -> "authorization_code")
    )

   WS.client.url(Urls.TokenEndpoint)
    .withHeaders("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8")
    .post(body.convert.mkString("", "&", "")).map {
     response => Ok(s"${response.body.toString}")
   }.recover { case th => Ok(s"failed ${th.getMessage}")}
  }

  def calendarList(access_token: String) = Action.async {
    WS.client.url(Urls.Calendar.calendarList).withQueryString(
      ("access_token" -> access_token)
    ).get().map {
      response => Ok(s"${response.body.toString}")
    }.recover { case th => Ok(s"${th.getMessage}")}
  }

  def insert(access_token: String) = Action.async {
    val request = WS.client.url(Urls.Calendar.calendarEventInsert("primary")).withQueryString(
      ("access_token" -> access_token),
      ("sendNotifications" -> "true")
    )
    val data = Json.obj(
      "summary" -> "Meeting",
      "description" -> "Business Meeting regarding project x",
      "location" -> "room 5, building A",
      "attachments" -> Json.obj("fileUrl" -> "http://add2cal.herokuapp.com"),
      "attendees" -> Json.arr(Json.obj("email" -> "pamu2java@gmail.com")),
      "start" -> Json.obj("date" -> JsNull, "dateTime" -> "2015-08-11T09:00:00-07:00", "timeZone" -> "Asia/Calcutta"),
      "end" -> Json.obj("date" -> JsNull, "dateTime" -> "2015-08-11T10:00:00-07:00", "timeZone" -> "Asia/Calcutta"),
      "reminders" -> Json.obj("useDefault" -> false, "overrides" -> Json.arr(Json.obj("method" -> "email", "minutes" -> "5")))
    )
    
    request.post(data).map {
      response => Ok(s"${response.body.toString}")
    }.recover {case th => Ok(s"${th.getMessage}")}
  }
}