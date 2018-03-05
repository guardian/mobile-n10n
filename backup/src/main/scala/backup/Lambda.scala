package backup

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import backup.logging.BackupLogging
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Lambda extends BackupLogging {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def withWsClient[A](block: WSClient => Future[A]): Future[A] = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    val ws = AhcWSClient()

    block(ws)
      .andThen { case _ => ws.close() }
      .andThen { case _ => actorSystem.terminate() }
  }

  def handler() : String = {
    val credentials = new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider(),
      new ProfileCredentialsProvider("mobile")
    )

    val config = Configuration.load(credentials)

    withWsClient { ws =>
      val backup = new Backup(config, ws)

      logger.info("Starting backup")
      backup.execute().andThen {
        case Success(_) =>
          logger.info("End of backup")
        case Failure(error) =>
          logger.error(error.getMessage, error)
      }
    }
   "Done"
  }

  def main(args: Array[String]): Unit = handler()
}
