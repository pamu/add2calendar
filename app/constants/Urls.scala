package constants

/**
 * Created by pnagarjuna on 09/08/15.
 */
object Urls {
  val GoogleOauth2 = "https://accounts.google.com/o/oauth2/auth"
  val TokenEndpoint = "https://www.googleapis.com/oauth2/v3/token"

  object Calendar {
    val calendarList = "https://www.googleapis.com/calendar/v3/users/me/calendarList"
    def calendarEventInsert(calendarId: String) = s"https://www.googleapis.com/calendar/v3/calendars/$calendarId/events"
    def calendarQuickAdd(calendarId: String) = s"https://www.googleapis.com/calendar/v3/calendars/$calendarId/events/quickAdd"
    def calendarUpdate(calendarId: String, eventId: String) = s"https://www.googleapis.com/calendar/v3/calendars/$calendarId/events/$eventId"
  }
}
