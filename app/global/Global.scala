package global

import models.DB
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

  override def onStart(app: Application): Unit = {
    super.onStart(app)
    Logger.info("Started Application")
    DB.init onComplete {
      case Success(sValue) => Logger info "Database init successful"
      case Failure(fValue) => Logger error s"Database init failed, reason ${fValue.getMessage}"
    }
  }

  override def onStop(app: Application): Unit = {
    super.onStop(app)
    system.shutdown()
  }
}
