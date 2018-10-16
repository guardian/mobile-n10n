package registration.services
import cats.data.EitherT
import cats.implicits._
import models.{DeviceToken, Registration, Topic, UniqueDeviceIdentifier}
import models.pagination.Paginated
import play.api.Logger
import registration.services.NotificationRegistrar.RegistrarResponse

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class CopyingRegistrar(
  val providerIdentifier: String,
  val mainRegistrar: NotificationRegistrar,
  val copyRegistrar: NotificationRegistrar
)(
  implicit ec: ExecutionContext
) extends NotificationRegistrar {

  private val logger = Logger(classOf[CopyingRegistrar])

  private def applyToMainAndCopy[A](function: NotificationRegistrar => RegistrarResponse[A]): RegistrarResponse[A] = {
    val mainResponse = function(mainRegistrar)
    // the copy is considered a side effect, if there's an error it will be dumped in the logs and swallowed
    val copyResponse = function(copyRegistrar)
    copyResponse.onComplete {
      case Failure(error) => logger.error(s"Unable to duplicate registration operation to ${copyRegistrar.providerIdentifier}", error)
      case Success(Left(error)) => logger.error(s"Unable to duplicate registration operation to ${copyRegistrar.providerIdentifier}: $error")
      case _ =>
    }
    mainResponse
  }

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] =
    applyToMainAndCopy(_.register(deviceToken, registration))

  override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] =
    applyToMainAndCopy(_.unregister(deviceToken))

  override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = {
    // rarely used (only by devs) we'll know this is running on the main registrar only
    mainRegistrar.findRegistrations(topic, cursor)
  }

  override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = {
    val mainResponseF = mainRegistrar.findRegistrations(deviceToken)
    val copyResponseF = copyRegistrar.findRegistrations(deviceToken)

    for {
      mainResponse <- EitherT(mainResponseF).getOrElse(Nil)
      copyResponse <- EitherT(copyResponseF).getOrElse(Nil)
    } yield Right(mainResponse ++ copyResponse)
  }

  override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = {
    // this method is here for GDPR compliance, executed on the main registrar as we don't store
    // the browser ID anymore
    mainRegistrar.findRegistrations(udid)
  }
}
