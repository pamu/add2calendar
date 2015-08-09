package actors

import javax.mail.{Message, NoSuchProviderException, MessagingException}

import akka.actor.{Status, Cancellable, ActorLogging, Actor}
import com.sun.mail.imap.IMAPFolder
import constants.Constants
import utils.JavaMailAPI
import utils.JavaMailAPI._
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
        case me: MessagingException =>
          log info("MessagingException reason {} cause {}", me.getMessage, me.getCause)
          sender ! me
        case nsp: NoSuchProviderException =>
          log info("NoSuchProviderException reason {} cause {}", nsp.getMessage, nsp.getCause)
          sender ! nsp
        case ex =>
          log info("exception {} of type {} reason {} caused {}", ex, ex.getClass, ex.getMessage, ex.getCause)
      }
    case msg => log info("Unknown message {} of type {}", msg, msg.getClass)
  }

  def attach(folder: IMAPFolder): Receive = {
    case AttachListener =>
      log info("Message AttachListener")
      JavaMailAPI.attachListener(folder, msgs => self ! Mails(msgs)) pipeTo self
    case AttachDone =>
      log info "Attach Done"
      context become idle(folder)
      self ! Idle
    case Status.Failure(th) => th match {
      case NoFolder(msg) =>
        log info("No folder {}", msg)
        context become connection
      case FolderClosed(msg) =>
        log info("FolderClosed {}", msg)
        context become connection
      case ex =>
        log info("exception {} of type {} reason {} cause {}", ex, ex.getClass, ex.getMessage, ex.getCause)
    }
    case msg => log info("Unknown message {} of type {}", msg, msg.getClass)
  }

  def idle(folder: IMAPFolder): Receive = {
    case Idle =>
      log info "Idle"
      JavaMailAPI.triggerIdle(folder) pipeTo self
    case ir: IdleResult => ir match {
      case IdleDone =>
        self ! Idle
        self ! NOOP
      case IdleException(th) =>
        context become connection
    }
    case NOOP =>
      log info "NOOP"
      JavaMailAPI.triggerNOOP(folder) pipeTo self
    case nr: NOOPResult => nr match {
      case NOOPDone =>
        context.system.scheduler.scheduleOnce(freq.getOrElse{5 minutes}.get.toMillis, self, NOOP)
      case NOOPFailure(th) =>
        context become connection
    }
    case msg => log info("Unknown message {} of type {}", msg, msg.getClass)
  }

}
