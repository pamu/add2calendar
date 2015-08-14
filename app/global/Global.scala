package global

import actors.SnifferManager
import akka.actor.Props
import models.{DBUtils, DB}
import play.api.libs.concurrent.Akka
import play.api.{Logger, Application, GlobalSettings}
import play.api.Play.current

import scala.util.{Failure, Success}

import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
 * Created by pnagarjuna on 09/08/15.
 */
object Global extends GlobalSettings {

  lazy val system = Akka.system

  lazy val snifferManager = system.actorOf(Props[SnifferManager], "SnifferManager")

  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Logger.info("Started Application")

    DB.init onComplete {
      case Success(value) => Logger info "DB init successful"
      case Failure(th) =>  {
        Logger info "DB init failed"
        th.printStackTrace()
      }
    }

    DBUtils.fetchUsers onComplete {
      case Success(x) => {
        x.foreach {
          pair => {
            Logger info s"starting ${pair}"
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

  override def onStop(app: Application): Unit = {
    super.onStop(app)
    system.shutdown()
  }
}
