package global

import actors.Sniffer
import actors.Sniffer._
import akka.actor.Props
import play.api.libs.concurrent.Akka
import play.api.{Logger, Application, GlobalSettings}
import play.api.Play.current
/**
 * Created by pnagarjuna on 09/08/15.
 */
object Global extends GlobalSettings {

  lazy val system = Akka.system

  lazy val sniffer = system.actorOf(Props(new Sniffer("imap.gmail.com", "nagarjuna@gozoomo.com", "palakurthy")), "Sniffer")

  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Logger.info("Started Application")
    sniffer ! Start
  }

  override def onStop(app: Application): Unit = {
    super.onStop(app)
    sniffer ! Stop
  }
}
