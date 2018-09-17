package registration.services.fcm

import cats.data.EitherT
import com.google.firebase.messaging.{FirebaseMessaging, TopicManagementResponse}
import models.pagination.Paginated
import models._
import play.api.Logger
import play.api.libs.json._
import providers.ProviderError
import registration.services.NotificationRegistrar.RegistrarResponse
import registration.services.{Configuration, NotificationRegistrar, RegistrationResponse, StoredRegistration}
import play.api.libs.ws.{WSClient, WSResponse}

import collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import cats.implicits._
import com.amazonaws.services.cloudwatch.model.StandardUnit
import metrics.{MetricDataPoint, Metrics}
import models.Provider.FCM

case class FcmProviderError(reason: String) extends ProviderError {
  override val providerName: String = Provider.FCM.value
}

case object InstanceNotFound extends ProviderError {
  override val providerName: String = Provider.FCM.value
  override val reason: String = "Instance not found"
}

class FcmRegistrar(
  firebaseMessaging: FirebaseMessaging,
  ws: WSClient,
  configuration: Configuration,
  metrics: Metrics,
  fcmExecutionContext: ExecutionContext
)(implicit ec: ExecutionContext) extends NotificationRegistrar {

  val logger = Logger(classOf[FcmRegistrar])

  override val providerIdentifier: String = Provider.FCM.value

  case class Instance(topics: List[Topic], platform: Platform)
  object Instance {
    implicit val instanceJF: Reads[Instance] = new Reads[Instance] {
      override def reads(json: JsValue): JsResult[Instance] = {
        for {
          topicMap <- (json \ "rel" \ "topics").validateOpt[Map[String, JsObject]]
          platform <- (json \ "platform").validate[Platform]
          topics = Topic.fromStringsIgnoringErrors(topicMap.getOrElse(Map.empty).keys.toList)
        } yield Instance(topics, platform)
      }
    }
  }

  private val instanceIdService = "https://iid.googleapis.com"

  // see https://developers.google.com/instance-id/reference/server
  private def fetchInstance(deviceToken: DeviceToken): RegistrarResponse[Instance] = {
    def responseToInstance(response: WSResponse): Either[ProviderError, Instance] = response.status match {
      case 200 => response.json.validate[Instance].fold(errors => Left(FcmProviderError(errors.mkString)), Right.apply)
      case 404 => Left(InstanceNotFound)
      case status => Left(FcmProviderError(s"Unable to fetch topics, got status $status back from the server"))
    }
    metrics.send(MetricDataPoint(name = "FcmRead", value = 1d, unit = StandardUnit.Count))

    val response = ws.url(s"$instanceIdService/iid/info/${deviceToken.fcmToken}")
      .addQueryStringParameters("details" -> "true")
      .addHttpHeaders("Authorization" -> s"key=${configuration.firebaseServerKey}")
      .get()

    response.map(responseToInstance)
  }

  private def executeFirebaseTopicOperation[A](description: String)(f: FirebaseMessaging => TopicManagementResponse): RegistrarResponse[Unit] = {
    metrics.send(MetricDataPoint(name = "FcmWrite", value = 1d, unit = StandardUnit.Count))
    val response = Future(f(firebaseMessaging))(fcmExecutionContext)
    response.map { r =>
      if (r.getSuccessCount != 1) {
        val errors = r.getErrors.asScala.toList.map(_.getReason).mkString(",")
        logger.error(s"Failed to $description, encountered following errors: $errors")
        Left(FcmProviderError(s"Failed to $description, encountered following errors: $errors"))
      } else Right(())
    } recover {
      case NonFatal(e) =>
        logger.error(s"Failed to $description", e)
        Left(FcmProviderError(s"Failed to $description: ${e.getMessage}"))
    }
  }

  private def subscribeToTopic(deviceToken: DeviceToken)(topic: Topic): RegistrarResponse[Unit] = {
    executeFirebaseTopicOperation(s"subscribe to topic ${topic.toFirebaseString}") {
      _.subscribeToTopic(List(deviceToken.fcmToken).asJava, topic.toFirebaseString)
    }
  }

  private def unsubscribeFromTopic(deviceToken: DeviceToken)(topic: Topic): RegistrarResponse[Unit] = {
    executeFirebaseTopicOperation(s"unsubscribe from topic ${topic.toFirebaseString}") {
      _.unsubscribeFromTopic(List(deviceToken.fcmToken).asJava, topic.toFirebaseString)
    }
  }

  override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = {
    def handleOperationError(results: Set[Either[ProviderError, Unit]]): Either[ProviderError, Unit] = {
      val errors = results.collect { case Left(error) => error }
      if (errors.isEmpty) {
        Right(())
      } else {
        Left(FcmProviderError(s"Unable to update the registration, encountered errors: ${errors.mkString(", ")}"))
      }
    }

    def forEachTopic(topics: Set[Topic])(f: Topic => RegistrarResponse[Unit]): RegistrarResponse[Unit] = {
      Future.sequence(topics.map(f)).map(handleOperationError)
    }

    def listExistingTopics: RegistrarResponse[Set[Topic]] = {
      fetchInstance(deviceToken).map {
        case Right(instance) => Right(instance.topics.toSet)
        case Left(InstanceNotFound) => Right(Set())
        case Left(error) => Left(error)
      }
    }

    val result = for {
      existingTopics <- EitherT(listExistingTopics)
      topicsToDelete = existingTopics -- registration.topics
      _ <- EitherT(forEachTopic(topicsToDelete)(unsubscribeFromTopic(deviceToken)))
      topicsToAdd = registration.topics -- existingTopics
      _ <- EitherT(forEachTopic(topicsToAdd)(subscribeToTopic(deviceToken)))
    } yield RegistrationResponse(
      deviceId = deviceToken.fcmToken,
      platform = registration.platform,
      topics = registration.topics,
      provider = FCM
    )

    result.value
  }

  override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = {
    ws.url(s"$instanceIdService/v1/web/iid/${deviceToken.fcmToken}")
      .addHttpHeaders("Authorization" -> s"key=${configuration.firebaseServerKey}")
      .delete()
      .map { response =>
        if (response.status >= 300) {
          logger.error(s"Unable to unregister from FCM, received response $response")
          Left(FcmProviderError(s"Unable to unregister from FCM, received ${response.status} return code"))
        } else {
          Right(())
        }
      }
  }

  override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = {
    // not possible with Firebase
    Future.successful(Right(Paginated.empty))
  }

  override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = {
    def instanceToStoredRegistrations(instance: Instance): List[StoredRegistration] =
      List(StoredRegistration(deviceToken.fcmToken, instance.platform, Set(), instance.topics.toSet, Provider.FCM.value))

    fetchInstance(deviceToken).map(_.map(instanceToStoredRegistrations))
  }

  override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = {
    // not supported as we never store the udid anymore
    Future.successful(Right(Paginated.empty))
  }
}
