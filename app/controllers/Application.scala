package controllers

import constants.Urls
import play.api.mvc.{Action, Controller}
import utils.WS

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }

  def oauth2 = Action {
    val uri = WS.client.url(Urls.GoogleOauth2).withQueryString(
      ("response_type" -> "code"),
      ("client_id" -> "55789062094-q4eq45sufglluim4f8ieqvnc0948jnra.apps.googleusercontent.com"),
      ("redirect_uri" -> "http://add2cal.herokuapp.com/oauth2callback"),
      ("scope" -> "https://www.googleapis.com/auth/calendar"),
      ("state" -> "scala"),
      ("access_type" -> "online"),
      ("approval_prompt" -> "force")
    ).uri.toString
    Redirect(uri)
  }

  def oauth2callback(state: Option[String], code: Option[String], error: Option[String]) = Action {
    Ok("hello")
  }
}