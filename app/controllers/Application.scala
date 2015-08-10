package controllers

import constants.{Constants, Urls}
import play.api.mvc.{Action, Controller}
import utils.WS

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }

  def oauth2 = Action {
    val url = WS.client.url(Urls.GoogleOauth2).withQueryString(
      ("scope" -> "https://www.googleapis.com/auth/calendar"),
      ("state" -> "scala"),
      ("response_type" -> "code"),
      ("client_id" -> s"${Constants.client_id}"),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("access_type" -> "online"),
      ("approval_prompt" -> "force")
    ).url.toString
    Redirect(url)
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
      ("code" -> code),
      ("client_id" -> Constants.client_id),
      ("client_secret" -> Constants.client_secret),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/ontoken"),
      ("grant_type" -> "authorization_code")
    )
   WS.client.url(Urls.TokenEndpoint)
    .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
    .post(body.mkString("", "&", "")).map {
     response => Ok(s"${response.toString}")
   }.recover { case th => Ok(s"failed ${th.getMessage}")}
  }

  def onToken(access_token: String, refresh_token: String, expires_in: String, token_type: String) = Action {
    Ok("Done")
  }
}