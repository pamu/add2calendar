package actors

import java.util.Properties
import javax.mail.event.{MessageCountEvent, MessageCountAdapter}
import javax.mail.{Session, Message}

import akka.actor.{Status, ActorLogging, Actor}
import com.sun.mail.imap.IMAPFolder
import constants.Constants

import scala.concurrent.Future
import akka.pattern.pipe

/**
 * Created by pnagarjuna on 05/08/15.
 */
object MailSniffer {
  case object Start
  case object Stop

  case class Email(msgs: List[Message])
  case object Register
  case class Idle(folder: IMAPFolder)
  case class WakeUp(folder: IMAPFolder)
}

object Utils {

  def getServerProps(host: String): Properties = {
    val props = new Properties()
    props put(String.format("mail.%s.host", Constants.PROTOCOL), host)
    props put(String.format("mail.%s.port", Constants.PROTOCOL), Constants.PORT)
    props put(String.format("mail.%s.socketFactory.class", Constants.PROTOCOL), "javax.net.ssl.SSLSocketFactory")
    props put(String.format("mail.%s.socketFactory.fallback", Constants.PROTOCOL), "false")
    props put(String.format("mail.%s.socketFactory.port", Constants.PROTOCOL), String.valueOf(Constants.PORT))
    props
  }

  def getBox(props: Properties, username: String, password: String, box: String): Future[IMAPFolder] = {
    Future {
      scala.concurrent.blocking {
        val session = Session.getDefaultInstance(props)
        val store = session.getStore(Constants.PROTOCOL)
        store.connect(username, password)
        store.getFolder(box).asInstanceOf[IMAPFolder]
      }
    }
  }

  def idle(folder: IMAPFolder): Future[Unit] = Future(scala.concurrent.blocking(folder.idle()))

}

class MailSniffer(host: String, username: String, password: String) extends Actor with ActorLogging {
  import MailSniffer._

  var retryCount = 0

  def receive: Receive = {
    case ex => log info("Unknown message {} of type {}", ex, ex getClass)
  }

  def init: Receive = {
    case Start =>
      import context.dispatcher
      Utils.getBox(Utils.getServerProps(host), username, password, Constants.INBOX) pipeTo self
    case box: IMAPFolder =>
      context become sniff(box)
      self ! WakeUp
    case Status.Failure(th) =>
      log info("MailSniffer terminated in init state reason: {}, cause {}", th.getMessage, th.getCause)
      context stop self
  }

  def sniff(folder: IMAPFolder): Receive = {
    case Email(msgs) =>

      log info("Emails")
      msgs.foreach(println)

    case Register =>

      if (folder.isOpen) {
        if (folder exists()) {
          folder.addMessageCountListener(new MessageCountAdapter {
            override def messagesAdded(e: MessageCountEvent): Unit = {
              super.messagesAdded(e)
              self ! Email(e.getMessages.toList)
            }
          })

          self ! Idle(folder)

        } else {
          log info("Requested box {} doesn't exists ", folder.getName)
          context stop self
        }
      } else {
        log info("Requested box {} is closed", folder.getName)
        context stop self
      }

    case Idle(folder) =>
      import context.dispatcher

      Utils.idle(folder) pipeTo self

    case unit: Unit =>

    case Status.Failure(th) =>

    case WakeUp(folder) =>

    case Stop => context stop self
  }
}
