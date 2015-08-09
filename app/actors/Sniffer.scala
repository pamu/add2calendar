package actors

import javax.mail.{Message, NoSuchProviderException, MessagingException}

import akka.actor.{Status, Cancellable, ActorLogging, Actor}
import com.sun.mail.imap.IMAPFolder
import constants.Constants
import utils.JavaMailAPI
import utils.JavaMailAPI.{NOOPDone, IdleDone}
import scala.concurrent.duration._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import akka.pattern.pipe

/**
 * Created by pnagarjuna on 08/08/15.
 */
object Sniffer {
  case object Start
  case object Stop
  case object Connect
  case object AttachListener
  case object Idle
  case object NOOP
  case class Mails(msgs: List[Message])
}

class Sniffer(host: String, username: String, password: String, freq: Option[Duration] = Some(5 minutes)) extends Actor with ActorLogging {

  import Sniffer._
  var noopScheduler: Option[Cancellable] = None

  override def preStart: Unit = log info "Sniffer Started"

  def receive = {
    case Start =>
      context become connection
      self forward Connect
    case _ =>
  }

  def connection: Receive = {
    case Connect =>
      log info "In connection state, Connect Message"
      JavaMailAPI
        .getIMAPFolder(Constants.PROTOCOL, host, Constants.PORT, username, password, Constants.INBOX) pipeTo self
    case folder: IMAPFolder =>
      log info "Got a folder from the connection"
      log info "Switching the state to Idle"
      context become idle(folder)
      self forward AttachListener
    case Status.Failure(th) =>
      log info "Failure in Connection state"
      th match {
        case me: MessagingException => sender ! me
        case nsp: NoSuchProviderException => sender ! nsp
        case ex =>
          log info("exception {} of type {} reason {} caused {}", ex, ex.getClass, ex.getMessage, ex.getCause)
      }
    case msg => log info("Unknown message {} of type {}", msg, msg.getClass)
  }

  def attach(folder: IMAPFolder): Receive = {
    case AttachListener =>
      JavaMailAPI.attachListener(folder, msgs => self ! Mails(msgs))

    case msg => log info("Unknown message {} of type {}", msg, msg.getClass)
  }

  def idle(folder: IMAPFolder): Receive = {
    case Idle =>
      JavaMailAPI.triggerIdle(folder) pipeTo self
      log info "Starting the scheduler"
      noopScheduler = Some(context.system.scheduler.schedule(0 seconds, freq.getOrElse(5 minutes).get.toMillis, self, NOOP))
    case IdleDone =>
    case NOOP =>
    case NOOPDone =>
    case Status.Failure(th) =>
    case msg => log info("Unknown message {} of type {}", msg, msg.getClass)
  }

}
