package controllers

import constants.{Constants, Urls}
import play.api.mvc.{Action, Controller}
import utils.WS

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }

  def oauth2 = Action {
    val uri = WS.client.url(Urls.GoogleOauth2).withQueryString(
      ("response_type" -> "code"),
      ("client_id" -> ""),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("scope" -> "https://www.googleapis.com/auth/calendar"),
      ("state" -> "scala"),
      ("access_type" -> "online"),
      ("approval_prompt" -> "force")
    ).uri.toString
    Redirect(uri)
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

  def onCode(code: String) = Action {
    val uri = WS.client.url(Urls.TokenEndpoint).withQueryString(
      ("code" -> code),
      ("client_id" -> Constants.client_id),
      ("client_secret" -> Constants.client_secret),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/ontoken"),
      ("grant_type" -> "authorization_code")
    ).uri.toString
    Redirect(uri)
  }

  def onToken(access_token: String, refresh_token: String, expires_in: String, token_type: String) = Action {
    Ok("Done")
  }
}