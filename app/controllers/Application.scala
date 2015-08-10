package controllers

import constants.{Constants, Urls}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import utils.WS

import play.api.libs.concurrent.Execution.Implicits.defaultContext

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
      ("access_type" -> "online"),
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
      ("redirect_uri" -> "http://add2cal.herokuapp.com/ontoken"),
      ("grant_type" -> "authorization_code")
    )
    
   WS.client.url(Urls.TokenEndpoint)
    .withHeaders("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8")
    .post(Json.toJson(body)).map {
     response => Ok(s"${response.toString}")
   }.recover { case th => Ok(s"failed ${th.getMessage}")}

  }

  def onToken(access_token: String, refresh_token: String, expires_in: String, token_type: String) = Action {
    Ok("Done")
  }
}