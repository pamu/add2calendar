package utils

import java.util.Properties
import javax.mail.event.{MessageCountEvent, MessageCountAdapter}
import javax.mail.{Message, Folder, Session}

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPFolder.ProtocolCommand
import com.sun.mail.imap.protocol.IMAPProtocol
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
 * Created by pnagarjuna on 08/08/15.
 */
object JavaMailAPI {

  def getServerProps(protocol: String, host: String, port: String): Properties = {
    val properties = new Properties()
    properties put(String.format("mail.%s.host", protocol), host)
    properties put(String.format("mail.%s.port", protocol), port)
    properties put(String.format("mail.%s.socketFactory.class", protocol), "javax.net.ssl.SSLSocketFactory")
    properties put(String.format("mail.%s.socketFactory.fallback", protocol), "false")
    properties put(String.format("mail.%s.socketFactory.port", protocol), String.valueOf(port))
    properties
  }

  def getIMAPFolder(protocol: String, host: String, port: String, username: String, password: String, box: String): Future[IMAPFolder] = {
    Future {
      scala.concurrent.blocking {
        val properties = getServerProps(protocol, host, port)
        val session = Session.getDefaultInstance(properties)
        val store = session.getStore(protocol)
        store.connect(username, password)
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_WRITE)
        inbox.asInstanceOf[IMAPFolder]
      }
    }
  }

  case class FolderClosed(msg: String) extends Exception(msg)
  case class NoFolder(msg: String) extends Exception(msg)
  case object AttachDone

  def attachListener(folder: IMAPFolder, f: List[Message] => Unit): Future[AttachDone.type] = {
    Future {
      scala.concurrent.blocking {
        if (folder.exists()) {
          if (folder.isOpen) {
            folder.addMessageCountListener(new MessageCountAdapter {
              override def messagesAdded(e: MessageCountEvent): Unit = {
                super.messagesAdded(e)
                f(e.getMessages.toList)
              }
            })
            AttachDone
          } else {
            throw FolderClosed(s"${folder.getName} is not open")
          }
        } else {
          throw NoFolder(s"${folder.getName} doesn't exist")
        }
      }
    }
  }

  sealed trait IdleResult
  case object IdleDone extends IdleResult
  case class IdleException(th: Throwable) extends IdleResult

  def triggerIdle(folder: IMAPFolder): Future[IdleResult] = {
    Future {
      scala.concurrent.blocking {
        if (folder.exists()) {
          if (folder.isOpen) {
            folder.idle()
            IdleDone
          } else {
            throw FolderClosed(s"${folder.getName} is not open")
          }
        } else {
          throw NoFolder(s"${folder.getName} doesn't exist")
        }
      }
    }.recover{case th => IdleException(th) }
  }

  sealed trait NOOPResult
  case object NOOPDone extends NOOPResult
  case class NOOPFailure(th: Throwable) extends NOOPResult

  def triggerNOOP(folder: IMAPFolder): Future[NOOPResult] = {
    Future {
      scala.concurrent.blocking {
        if (folder.exists()) {
          if (folder.isOpen) {
            folder.doCommand(new ProtocolCommand {
              override def doCommand(imapProtocol: IMAPProtocol): AnyRef = {
                imapProtocol.simpleCommand("NOOP", null)
                return null
              }
            })
            NOOPDone
          } else {
            throw FolderClosed(s"${folder.getName} is not open")
          }
        } else {
          throw NoFolder(s"${folder.getName} doesn't exist")
        }
      }
    }.recover{case th => NOOPFailure(th) }
  }

}
