package controllers

import java.sql.Timestamp
import java.util.Date

import actors.SnifferManager
import constants.{Constants, Urls}
import global.Global
import models._
import play.api.Logger
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

  def oauth2(state: String) = Action {
    val params = Map[String, String](
      ("scope" -> "https://www.googleapis.com/auth/calendar"),
      ("state" -> state),
      ("response_type" -> "code"),
      ("client_id" -> s"${Constants.client_id}"),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("access_type" -> "offline"),
      ("approval_prompt" -> "force")
    ).convert.mkString("?", "&", "").toString

    val requestURI = s"${Urls.GoogleOauth2}${params}"

    Redirect(requestURI)
  }

  def oauth2callback(state: String, code: Option[String], error: Option[String]) = Action {
    code match {
      case Some(code) => Redirect(routes.Application.onCode(state, code))
      case None => {
        error match {
          case Some(err) => Redirect(routes.Application.home()).flashing("failure" -> s"Google server error, error: $err")
          case None => Redirect(routes.Application.home())
        }
      }
    }
  }

  def onCode(state: String, code: String) = Action.async {
    val body = Map[String, String](
      ("code" -> s"$code"),
      ("client_id" -> s"${Constants.client_id}"),
      ("client_secret" -> s"${Constants.client_secret}"),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("grant_type" -> "authorization_code")
    )

   WS.client.url(Urls.TokenEndpoint)
    .withHeaders("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8")
    .post(body.convert.mkString("", "&", "")).flatMap {
     response => {
       val tokens =Json.parse(response.body)
       val refreshTime = RefreshTime((tokens \ "access_token").asOpt[String].get, (tokens \ "refresh_token").asOpt[String].get, new Timestamp(new Date().getTime), (tokens \ "expires_in").asOpt[Long].get, state.toLong)
        DBUtils.createRefreshTime(refreshTime).flatMap {
          id => {
            if (id > 0) {
              DBUtils.getUser(state.toLong).map {
                user =>  {

                  Global.snifferManager ! SnifferManager.StartSniffer((user, refreshTime.copy(id = Some(id))))

                  Redirect(routes.Application.status()).flashing("success" -> "Done")
                }
              }.recover {case th  => Redirect(routes.Application.status()).flashing("failure" -> "Cannot extract user")}

            } else {
              Future(Redirect(routes.Application.home()).flashing("failure" -> "problem storing refresh time"))
            }
          }
        }.recover {case th => Redirect(routes.Application.home()).flashing("failure" -> "problem storing refresh time")}
     }
   }.recover { case th => Ok(s"failed ${th.getMessage}")}
  }

  def refreshToken(state: String, refreshToken: String) = Action.async {
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
        Logger info s"refresh tokens: $tokens"
        val refreshTime = RefreshTime((tokens \ "access_token").asOpt[String].get, refreshToken
          , new Timestamp(new Date().getTime), (tokens \ "expires_in").asOpt[Long].get, state.toLong)
        DBUtils.createRefreshTime(refreshTime).flatMap {
          id =>
              DBUtils.getUser(state.toLong).map {
                user =>  {

                  Global.snifferManager ! SnifferManager.StartSniffer((user, refreshTime.copy(id = Some(id))))

                  Redirect(routes.Application.status()).flashing("success" -> "Done")
                }
              }.recover {case th  => Redirect(routes.Application.status()).flashing("failure" -> "Cannot extract user")}
        }.recover {case th => Redirect(routes.Application.home()).flashing("failure" -> "problem storing refresh time")}
      }
    }.recover { case th => {
      th.printStackTrace()
      Ok(s"failed ${th.getMessage} ${th.getCause}")
    }}
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

                          Global.snifferManager ! SnifferManager.StartSniffer((user, refreshTime))


                          Future(Redirect(routes.Application.refreshToken(user.id.get.toString, refreshTime.refreshToken)))

                          /**
                          val millis = System.currentTimeMillis() - refreshTime.refreshTime.getTime
                          if ((millis/1000) < (refreshTime.refreshPeriod - 60)) {
                            Future(Redirect(routes.Application.status()).flashing("success" -> "Status Ok"))
                            //Future(Ok("Create Calendar event"))
                          } else {
                            Future(Redirect(routes.Application.refreshToken(user.id.get.toString, refreshTime.refreshToken)))
                          } **/

                        }
                        case None => {
                          Future(Redirect(routes.Application.oauth2(user.id.get.toString)))
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
                          Redirect(routes.Application.oauth2(id.toString))
                        } else {
                          Redirect(routes.Application.home()).flashing("failure" -> "Couldn't create new user, try again")
                        }
                      }
                    }.recover { case th => {
                      th.printStackTrace()
                      Redirect(routes.Application.home()).flashing("failure" -> "Error creating user, try again.")
                    }}
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

  def status = Action {
    Ok(views.html.status())
  }
}