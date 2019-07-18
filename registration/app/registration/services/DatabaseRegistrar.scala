package registration.services

import cats.effect.IO
import db.RegistrationService
import models._
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

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {

    def toDBRegistration(topic: Topic) = db.Registration(
      device = db.Device(deviceToken.token, registration.platform),
      topic = db.Topic(topic.toString),
      shard = db.Shard.fromToken(deviceToken)
    )

    val insertedRegistrations = for {
      _ <- registrationService.removeAllByToken(deviceToken.token)
      dbRegistrations = registration.topics.toList.map(toDBRegistration)
      insertionResults <- dbRegistrations.map(registrationService.save).sequence: IO[List[Int]]
    } yield insertionResults.sum

    val latencyStart = System.currentTimeMillis
    val result = insertedRegistrations.map { _ =>
      Right(RegistrationResponse(
        deviceId = deviceToken.token,
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
    registrationService.removeAllByToken(deviceToken.token).unsafeToFuture.map(_ => Right(()))
  }
}
