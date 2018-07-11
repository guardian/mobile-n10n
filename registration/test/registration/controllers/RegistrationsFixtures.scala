package registration.controllers

import application.WithPlayApp
import error.NotificationsError
import models.TopicTypes.{Breaking, FootballMatch}
import models._
import models.pagination.Paginated
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponents, Configuration => PlayConfig}
import play.api.libs.ws.WSClient
import providers.ProviderError
import registration.RegistrationApplicationComponents
import registration.services.topic.{TopicValidator, TopicValidatorError}
import registration.services._

import scala.concurrent.duration._
import scala.concurrent.Future

trait DelayedRegistrationsBase extends RegistrationsBase {
  override lazy val fakeNotificationRegistrar: NotificationRegistrar = new NotificationRegistrar {
    override val providerIdentifier: String = "test"

    override def register(deviceId: String, registration: Registration): Future[Either[ProviderError, RegistrationResponse]] = Future.successful {
      Thread.sleep(2000)
      Right(RegistrationResponse(
        deviceId = "deviceAA",
        platform = Android,
        topics = registration.topics
      ))
    }

    override def unregister(pushToken: String): Future[Either[ProviderError, Unit]] =
      Future.successful(Right(()))

    override def findRegistrations(topic: Topic, cursor: Option[String]): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???

    override def findRegistrations(pushToken: String): Future[Either[ProviderError, List[StoredRegistration]]] = ???

    override def findRegistrations(udid: UniqueDeviceIdentifier): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???
  }
}

trait RegistrationsBase extends WithPlayApp with RegistrationsJson {

  val wsClient: WSClient

  val footballMatchTopic = Topic(`type` = FootballMatch, name = "science")

  val breakingNewsUk = Topic(`type` = Breaking, name = "uk")

  val topics = Set(footballMatchTopic, breakingNewsUk)

  val legacyTopics = Set(breakingNewsUk)

  lazy val fakeTopicValidator = new TopicValidator {
    override def removeInvalid(topics: Set[Topic]): Future[Either[TopicValidatorError, Set[Topic]]] = {
      Future.successful(Right(topics))
    }
  }

  lazy val fakeNotificationRegistrar = new NotificationRegistrar {
    override val providerIdentifier = "test"

    private var registrations = List.empty[Registration]

    override def register(deviceId: String, registration: Registration): Future[Either[ProviderError, RegistrationResponse]] = Future.successful {
      registrations = registration :: registrations
      Right(RegistrationResponse(
        deviceId = "deviceAA",
        platform = Android,
        topics = registration.topics
      ))
    }

    override def unregister(pushToken: String): Future[Either[ProviderError, Unit]] =
      Future.successful(Right(()))

    override def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[Either[ProviderError, Paginated[StoredRegistration]]] = {
      val selected = if (cursor.contains("abc")) {
        registrations.filter(_.topics.contains(topic)).map(StoredRegistration.fromRegistration).drop(5)
      } else {
        registrations.filter(_.topics.contains(topic)).map(StoredRegistration.fromRegistration).take(5)
      }
      Future.successful(Right(Paginated(selected.toList, None)))
    }

    override def findRegistrations(pushToken: String): Future[Either[ProviderError, List[StoredRegistration]]] = {
      val selected = registrations.filter(_.deviceId == pushToken).map(StoredRegistration.fromRegistration)
      Future.successful(Right(selected.toList))
    }

    override def findRegistrations(udid: UniqueDeviceIdentifier): Future[Either[ProviderError, Paginated[StoredRegistration]]] = ???
  }

  lazy val fakeRegistrarProvider = new RegistrarProvider {
    override def registrarFor(platform: Platform, buildTier: Option[String]): Either[NotificationsError, NotificationRegistrar] = Right(fakeNotificationRegistrar)

    override def withAllRegistrars[T](fn: (NotificationRegistrar) => T): List[T] = List(fn(fakeNotificationRegistrar))
  }

  override def configureComponents(context: Context): BuiltInComponents = {
    new RegistrationApplicationComponents(context)  {
      override lazy val topicValidator = fakeTopicValidator
      override lazy val registrarProvider: RegistrarProvider = fakeRegistrarProvider
      override lazy val appConfig = new Configuration(PlayConfig.empty) {
        override lazy val defaultTimeout = 1.seconds
        override lazy val newsstandShards: Int = 10
      }
    }
  }
}

trait RegistrationsJson {
  val legacyIosRegistrationWithFootballMatchTopicJson =
    """
      |{
      |	"device": {
      |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
      |		"buildTier": "debug",
      |		"platform": "ios"
      |	},
      |	"preferences": {
      |		"edition": "UK",
      |		"topics": [
      |     {"type": "football-match", "name": "science"},
      |			{"type": "breaking", "name": "uk"}
      |		],
      |		"receiveNewsAlerts": true
      |	}
      |}
    """.stripMargin

  val legacyIosRegistrationJson =
    """
      |{
      |	"device": {
      |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
      |		"buildTier": "debug",
      |		"platform": "ios"
      |	},
      |	"preferences": {
      |		"edition": "UK",
      |		"topics": [{
      |			"type": "user-type",
      |			"name": "GuardianInternalBeta"
      |		}],
      |		"receiveNewsAlerts": true
      |	}
      |}
    """.stripMargin

  val legacyAndroidRegistrationJson =
    """
      |{
      |	"device": {
      |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
      |		"buildTier": "debug",
      |		"platform": "android"
      |	},
      |	"preferences": {
      |		"edition": "UK",
      |		"topics": [{
      |			"type": "user-type",
      |			"name": "GuardianInternalBeta"
      |		}],
      |		"receiveNewsAlerts": true
      |	}
      |}
    """.stripMargin

  val registrationJson =
    """
      |{
      |  "deviceId": "someId",
      |  "platform": "android",
      |  "topics": [
      |    {"type": "football-match", "name": "science"},
      |    {"type": "breaking", "name": "uk"}
      |  ]
      |}
    """.stripMargin
}