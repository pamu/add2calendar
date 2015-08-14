package actors

import javax.mail.AuthenticationFailedException

import akka.actor.SupervisorStrategy.{Stop, Restart}
import akka.actor._
import models.{User, RefreshTime}
import scala.concurrent.duration._
/**
 * Created by pnagarjuna on 13/08/15.
 */
object SnifferManager {
  case class StartSniffer(pair: (User, RefreshTime))
  case class StopSniffer(username: String)
  case class StopSnifferWithId(id: Long)
}

class SnifferManager extends Actor with ActorLogging {

  import SnifferManager._

  var workers = Map.empty[String, ActorRef]


  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 2 minute) {
    case _: AuthenticationFailedException => Stop
    case _: Exception                     => Restart
  }

  def receive = {
    case StartSniffer(pair) => {
      val sniffer = context.actorOf(Props(new Sniffer(pair._1.host, pair._1.email, pair._1.pass, pair._2)), "Sniffer"+pair._1.id.get)
      context watch sniffer
      workers += (pair._1.email -> sniffer)
    }
    case StopSniffer(username) => {
      if (workers contains username) {
        workers(username) ! Sniffer.Stop
      }
    }
    case StopSnifferWithId(id) => {

    }
    case Terminated(actor) => {
      workers = workers.filter(pair => pair._2 != actor)
    }
    case _ => log info s"Unknown message in Sniffer Manager"
  }
}
