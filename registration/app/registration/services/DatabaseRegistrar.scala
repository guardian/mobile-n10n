package registration.services

import cats.effect.IO
import db.RegistrationService
import models._
import registration.services.NotificationRegistrar.RegistrarResponse
import fs2.Stream
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{MetricDataPoint, Metrics}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DatabaseRegistrar(
  registrationService: RegistrationService[IO, Stream],
  metrics: Metrics
)(implicit ec: ExecutionContext) extends NotificationRegistrar {
  def dbHealthCheck(): Future[List[TopicCount]] = {
    val simpleSelect = registrationService.simpleSelectForHealthCheck()
    simpleSelect.compile.toList.unsafeToFuture()
  }


  override val providerIdentifier: String = "DatabaseRegistrar"

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {

    def toDBRegistration(topic: Topic) = db.Registration(
      device = db.Device(deviceToken.token, registration.platform),
      topic = db.Topic(topic.toString),
      shard = db.Shard.fromToken(deviceToken),
      buildTier = db.BuildTier.chooseTier(
        buildTier = registration.buildTier,
        platform = registration.platform,
        appVersion = registration.appVersion
      )
    )

    val dbRegistrations = registration.topics.toList.map(toDBRegistration)
    val insertedRegistrations = registrationService.registerDevice(deviceToken.token, dbRegistrations)

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
        metrics.send(MetricDataPoint(name = "RegistrationInsertionLatency", value = (System.currentTimeMillis - latencyStart).toDouble, unit = StandardUnit.Milliseconds))
      case Failure(_) =>
        metrics.send(MetricDataPoint(name = "FailedRegistrationInsertion", value = 1d, unit = StandardUnit.Count))
    }

    result
  }
}
