package batches

import java.io.File

import play.api.inject.guice.GuiceApplicationLoader
import play.api.{Logger, ApplicationLoader, Mode, Environment}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

trait Batch {
  def execute(): Future[Unit]
}

object Batch {
  def run[T <: Batch](rootPath: String)(implicit classTag: ClassTag[T]): Unit = {
    val env = Environment(new File(rootPath), this.getClass.getClassLoader, Mode.Prod)
    val ctx = ApplicationLoader.createContext(env)
    val app = new GuiceApplicationLoader().load(ctx)
    val className = classTag.runtimeClass.getName

    try {
      val backup = app.injector.instanceOf[T]

      Logger.info(s"Starting $className")
      backup.execute().onComplete {
        case Success(_) =>
          Logger.info(s"End of $className")
          app.stop()
        case Failure(error) =>
          Logger.error(error.getMessage, error)
          app.stop()
      }
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        app.stop()
    }
  }
}
