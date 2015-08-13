package controllers

import constants.{Constants, Urls}
import models.{User, DBUtils, DB, IMAPCredentials}
import play.api.data.Form
import play.api.libs.json.{JsNull, Json}
import play.api.mvc.{Action, Controller}
import utils.{JavaMailAPI, WS}

import play.api.data.Forms._

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

object Application extends Controller {

  def index = Action {
    Redirect(routes.Application.home())
    //Ok(views.html.index("Hello Play Framework"))
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

  def refreshToken(refreshToken: String) = Action.async {
    val body = Map[String, String](
      ("client_id" -> Constants.client_id),
      ("client_secret" -> Constants.client_secret),
      ("refresh_token" -> refreshToken),
      ("grant_type" -> "refresh_token")
    )

    WS.client.url(Urls.TokenEndpoint)
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8")
      .post(body.convert.mkString("", "&", "")).map {
      response => Ok(s"{${response.body.toString}}")
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

  def sniffer(email: String, pass: String) = Action {
    Ok("")
  }

  def quickAdd(access_token: String, text: String) = Action.async {
    val request = WS.client.url(Urls.Calendar.calendarQuickAdd("primary")).withQueryString(
      ("access_token" -> access_token),
      ("text" -> text),
      ("sendNotifications" -> "true")
    )
    val response = request.post("")
    response.map {
      res => Ok(s"${res.body.toString}")
    }.recover { case th => Ok(s"${th.getMessage}")}
  }

  val assistantMailForm = Form(
    mapping("host" -> nonEmptyText,
            "email" -> email,
            "pass" -> nonEmptyText(minLength = 8, maxLength = 20)
    )(IMAPCredentials apply)(IMAPCredentials unapply )
  )

  def home = Action { implicit request =>
    Ok(views.html.home(assistantMailForm))
  }

  def assistantEmailFormPost = Action.async { implicit request =>
    assistantMailForm.bindFromRequest().fold(
      hasErrors => Future(BadRequest(views.html.home(hasErrors))),
      imapCredentials => {
        val folderFuture = JavaMailAPI.getIMAPFolder(Constants.PROTOCOL, imapCredentials.host, Constants.PORT, imapCredentials.email, imapCredentials.password, Constants.INBOX)
        folderFuture.flatMap {
          folder => {
            if (folder.exists() && folder.isOpen) {
              DBUtils.fetchUser(imapCredentials.email).flatMap {
                optionUser => optionUser match {
                  case Some(user) => {
                    DBUtils.refreshTime(user.email).flatMap {
                      optionRefreshTime => optionRefreshTime match {
                        case Some(refreshTime) => {
                          val millis = System.currentTimeMillis() - refreshTime.refreshTime.getTime
                          if (millis < (refreshTime.refreshPeriod - 60)) {
                            Future(Ok("Create Calendar event"))
                          } else {
                            Future(Redirect(routes.Application.refreshToken(refreshTime.refreshToken)))
                          }
                        }
                        case None => {
                          Future(Redirect(routes.Application.oauth2()))
                        }
                      }
                    }.recover { case th => {
                      Ok("Error fetching refresh details from db")
                    }}
                  }
                  case None => {
                    DBUtils.createUser(User(imapCredentials.host, imapCredentials.email, imapCredentials.password)).map {
                      id => {
                        if (id > 0) {
                          Redirect(routes.Application.oauth2())
                        } else {
                          Redirect(routes.Application.home()).flashing("failure" -> "Couldn't create new user, try again")
                        }
                      }
                    }.recover { case th => Redirect(routes.Application.home()).flashing("failure" -> "Error creating user, try again.")}
                  }
                }
              }.recover { case th => Redirect(routes.Application.home()).flashing("failure" -> "Error fetching user")}
            } else {
              Future(Redirect(routes.Application.home()).flashing("failure" -> "Improper credentials"))
            }
          }
        }.recover { case th =>
          Redirect(routes.Application.home())
          .withNewSession
          .flashing("failure" -> s"Improper credentials reason : ${th.getMessage} cause: ${th.getCause}")
        }
      }
    )
  }
}