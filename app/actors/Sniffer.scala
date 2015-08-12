package actors

import javax.mail.{Message, NoSuchProviderException, MessagingException}

import akka.actor.{ActorLogging, Status, Actor}
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

class Sniffer(host: String, username: String, password: String, freq: Option[FiniteDuration] = Some(5 minutes)) extends Actor with ActorLogging {

  import Sniffer._

  override def preStart: Unit = log info "Sniffer Started"

  def receive = {
    case Start =>
      log info "Start Message"
      context become connection
      self forward Connect
    case Stop => context stop self
    case msg => log info(s"Unknown message $msg of type ${msg.getClass}")
  }

  def connection: Receive = {
    case Connect =>
      log info "In connection state, Connect Message"
      JavaMailAPI
        .getIMAPFolder(Constants.PROTOCOL, host, Constants.PORT, username, password, Constants.INBOX) pipeTo self
    case folder: IMAPFolder =>
      log info "Got a folder from the connection"
      log info "Switching the state to AttachListener state"
      context become attach(folder)
      self forward AttachListener
    case Status.Failure(th) =>
      log info "Failure in Connection state"
      th match {
        case nspe: NoSuchProviderException => {
          log info(s"NoSuchProviderException reason ${nspe.getMessage} cause ${nspe.getCause}")
          sender ! nspe
        }
        case me: MessagingException => {
          log info(s"MessagingException reason ${me.getMessage} cause ${me.getCause}")
          sender ! me
        }
        case ex =>
          log info(s"exception $ex of type ${ex.getClass} reason ${ex.getMessage} caused ${ex.getCause}")
      }
    case Stop => context stop self
    case msg => log info(s"Unknown message $msg of type ${msg.getClass}")
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
      case NoFolder(msg) => {
        log info(s"No folder $msg")
        context become connection
        self ! Connect
      }
      case FolderClosed(msg) => {
        log info(s"FolderClosed $msg")
        context become connection
        self ! Connect
      }
      case ex =>
        log info(s"exception $ex of type ${ex.getClass} reason ${ex.getMessage} cause ${ex.getCause}")
    }
    case Stop => context stop self
    case msg => log info(s"Unknown message $msg of type ${msg.getClass}")
  }

  def idle(folder: IMAPFolder): Receive = {
    case Mails(msgs) =>
      msgs.foreach(msg => println(s"${msg.getSubject} from ${msg.getFrom.mkString(" => ", ",", " <= ")}"))
     case Idle =>
       log info "Idle"
       JavaMailAPI.triggerIdle(folder) pipeTo self
       context.system.scheduler.scheduleOnce(freq.getOrElse(5 minutes), self, NOOP)
    case ir: IdleResult => ir match {
      case IdleDone => {
        log info "Idle Done"
        self ! Idle
        //context.system.scheduler.scheduleOnce(freq.getOrElse(5 minutes), self, NOOP)
      }
      case IdleException(th) => {
        log info "Idle failure"
        context become connection
        self ! Connect
      }
    }
    case NOOP =>
      log info "NOOP"
      JavaMailAPI.triggerNOOP(folder) pipeTo self
    case nr: NOOPResult => nr match {
      case NOOPDone => {
        log info "NOOP Done"
        //context.system.scheduler.scheduleOnce(freq.getOrElse(5 minutes), self, NOOP)
      }
      case NOOPFailure(th) => {
        log info "NOOP failure"
        context become connection
        self ! Connect
      }
    }
    case Stop => context stop self
    case msg => log info(s"Unknown message $msg of type ${msg.getClass}")
  }

}
