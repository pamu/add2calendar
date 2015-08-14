package actors

import javax.mail.event.{MessageCountEvent, MessageCountAdapter}
import javax.mail.{AuthenticationFailedException, Message, NoSuchProviderException, MessagingException}

import akka.actor._
import com.sun.mail.imap.IMAPFolder
import constants.Constants
import models.RefreshTime
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

class Sniffer(host: String, username: String, password: String, refreshTime: RefreshTime, freq: Option[FiniteDuration] = Some(5 minutes)) extends Actor with ActorLogging {

  import Sniffer._

  override def preStart: Unit = {
    log info "Sniffer Started"
    val gcalm = context.actorOf(Props(new GCalManager(refreshTime)))
    this.gcalm = Some(gcalm)
  }

  var gcalm: Option[ActorRef] = None

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
      folder.addMessageCountListener(new MessageCountAdapter {
        override def messagesAdded(e: MessageCountEvent): Unit = {
          super.messagesAdded(e)
          self forward Mails(e.getMessages.toList)
        }
      })
      context become idle(folder)
      self forward Idle
    case Status.Failure(th) =>
      log info "Failure in Connection state"
      context.system.scheduler.scheduleOnce(1 minutes, self, Connect)
      th match {
        case afe: AuthenticationFailedException => {
          log info (s"Authentication Failed Exception reason ${afe.getMessage} cause ${afe.getClass}")
          //sender ! afe
        }
        case nspe: NoSuchProviderException => {
          log info(s"NoSuchProviderException reason ${nspe.getMessage} cause ${nspe.getCause}")
          //sender ! nspe
        }
        case me: MessagingException => {
          log info(s"MessagingException reason ${me.getMessage} cause ${me.getCause}")
          //sender ! me
        }
        case ex =>
          log info(s"exception $ex of type ${ex.getClass} reason ${ex.getMessage} caused ${ex.getCause}")
          //sender ! ex
      }
    case Stop =>
      log debug "Stop Message, Stopping the Sniffer"
      context stop self
    case msg => log info(s"Unknown message $msg of type ${msg.getClass}")
  }

  def idle(folder: IMAPFolder): Receive = {
    case Mails(msgs) =>
      msgs.foreach(msg => {
        gcalm.map(_ ! GCalManager.CreateEvent(msg))
      })
      msgs.foreach(msg => println(s"${msg.getSubject} from ${msg.getFrom.mkString(" => ", ",", " <= ")}"))
      //sender ! Mails(msgs)
     case Idle =>
       log info "Idle"
       JavaMailAPI.triggerIdle(folder) pipeTo self
       context.system.scheduler.scheduleOnce(freq.getOrElse(5 minutes), self, NOOP)
    case ir: IdleResult => ir match {
      case IdleDone => {
        log info "Idle Done"
        self forward Idle
        //context.system.scheduler.scheduleOnce(freq.getOrElse(5 minutes), self, NOOP)
      }
      case IdleException(th) => {
        log info "Idle failure"
        context become connection
        self forward Connect
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
        self forward Connect
      }
    }
    case Stop => context stop self
    case msg => log info(s"Unknown message $msg of type ${msg.getClass}")
  }

}
