package backup

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import backup.logging.BackupLogging
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Lambda extends BackupLogging {

  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    InstanceProfileCredentialsProvider.getInstance
  )

  val config = Configuration.load(credentials)

  def handler() : String = {

    val wSClient = AhcWSClient()
    val backup = new Backup(config, wSClient)

    logger.info("Starting backup")
    backup.execute().onComplete {
      case Success(_) =>
        logger.info("End of backup")
        wSClient.close()
        actorSystem.terminate()
      case Failure(error)  =>
        logger.error(error.getMessage, error)
        wSClient.close()
        actorSystem.terminate()
    }
   "Done"
  }

}
