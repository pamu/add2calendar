package global

import actors.SnifferManager
import akka.actor.Props
import models.{DBUtils, DB}
import play.api.libs.concurrent.Akka
import play.api.{Logger, Application, GlobalSettings}
import play.api.Play.current

import scala.util.{Failure, Success}

/**
 * Created by pnagarjuna on 09/08/15.
 */
object Global extends GlobalSettings {

  lazy val system = Akka.system

  lazy val snifferManager = system.actorOf(Props[SnifferManager], "SnifferManager")

  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Logger.info("Started Application")
    DB.init.value match {
      case Some(value) => {
        value match {
          case Success(x) => {
            Logger info "DB init success"
          }
          case Failure(th) => {
            Logger debug "DB init failed"
            th.printStackTrace()
          }
        }
      }
      case None => {
        Logger info "Database init returned None"
      }
    }

    DBUtils.fetchUsers.value match {
      case Some(value) => {
        value match {
          case Success(x) => {
            x.foreach {
              pair => {
                snifferManager ! SnifferManager.StartSniffer(pair)
              }
            }
          }
          case Failure(th) => {
            Logger info "fetch users call failed"
            th.printStackTrace()
          }
        }
      }
      case None => {
        Logger info "fetch users returned None"
      }
    }
  }

  override def onStop(app: Application): Unit = {
    super.onStop(app)
    system.shutdown()
  }
}
