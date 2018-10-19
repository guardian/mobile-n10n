package registration.services
import cats.effect.IO
import db.RegistrationService
import models._
import models.pagination.Paginated
import registration.services.NotificationRegistrar.RegistrarResponse
import fs2.Stream
import cats.implicits._
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{MetricDataPoint, Metrics}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DatabaseRegistrar(
  registrationService: RegistrationService[IO, Stream],
  metrics: Metrics
)(implicit ec: ExecutionContext) extends NotificationRegistrar {
  override val providerIdentifier: String = "DatabaseRegistrar"

  private def extractToken(deviceToken: DeviceToken, platform: Platform): String = platform match {
    case Android => deviceToken.fcmToken
    case _ => deviceToken.azureToken
  }

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    val token = extractToken(deviceToken, registration.platform)

    def toDBRegistration(topic: Topic) = db.Registration(
      device = db.Device(token, registration.platform),
      topic = db.Topic(topic.toString),
      shard = db.Shard.fromToken(token)
    )

    val insertedRegistrations = for {
      _ <- registrationService.removeAllByToken(token)
      dbRegistrations = registration.topics.toList.map(toDBRegistration)
      insertionResults <- dbRegistrations.map(registrationService.save).sequence: IO[List[Int]]
    } yield insertionResults.sum

    val latencyStart = System.currentTimeMillis
    val result = insertedRegistrations.map { _ =>
      Right(RegistrationResponse(
        deviceId = token,
        platform = registration.platform,
        topics = registration.topics,
        provider = Provider.Guardian
      ))
    }.unsafeToFuture()

    result.onComplete {
      case Success(_) =>
        metrics.send(MetricDataPoint(name = "SuccessfulRegistrationInsertion", value = 1d, unit = StandardUnit.Count))
        metrics.send(MetricDataPoint(name = "RegistrationInsertionLatency", value = System.currentTimeMillis - latencyStart, unit = StandardUnit.Milliseconds))
      case Failure(_) =>
        metrics.send(MetricDataPoint(name = "FailedRegistrationInsertion", value = 1d, unit = StandardUnit.Count))
    }

    result
  }

  override def unregister(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[Unit] = {
    registrationService.removeAllByToken(extractToken(deviceToken, platform)).unsafeToFuture.map(_ => Right(()))
  }

  override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = {
    Future.successful(Right(Paginated.empty))
  }

  override def findRegistrations(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[List[StoredRegistration]] = {
    def dbRegistrationToStoredRegistration(dbRegistrations: List[db.Registration]): List[StoredRegistration] = {
      if (dbRegistrations.isEmpty) Nil else {
        val first = dbRegistrations.head
        val topics = dbRegistrations
          .map(_.topic.name)
          .map(Topic.fromString)
          .flatMap(_.toOption)
          .toSet
        List(StoredRegistration(
          deviceId = first.device.token,
          platform = first.device.platform,
          tagIds = Set.empty,
          topics = topics,
          provider = Provider.Guardian.value
        ))
      }
    }
    registrationService
      .findByToken(extractToken(deviceToken, platform))
      .compile.toList.unsafeToFuture
      .map(dbRegistrationToStoredRegistration)
      .map(Right.apply)
  }

  override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = {
    Future.successful(Right(Paginated.empty))
  }
}
