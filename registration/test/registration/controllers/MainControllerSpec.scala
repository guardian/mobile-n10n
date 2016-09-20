package registration.controllers

import application.WithPlayApp
import error.NotificationsError
import models.TopicTypes.{Breaking, FootballMatch}
import models._
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponents, BuiltInComponentsFromContext}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import providers.ProviderError
import registration.AppComponents
import registration.services.topic.{TopicValidator, TopicValidatorError}
import registration.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.data.Xor
import cats.implicits._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import azure.UdidNotFound
import _root_.azure.Registrations
import models.pagination.Paginated

class MainControllerSpec extends PlaySpecification with JsonMatchers with Mockito {

  "Registrations controller" should {
    "responds to healtcheck" in new registrations {
      val eventualResult = route(app, FakeRequest(GET, "/healthcheck")).get

      status(eventualResult) must equalTo(OK)
    }

    "accepts registration and calls registrar factory" in new registrations {
      val Some(result) = route(app, FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                    /("name" -> "science")
    }

    "accepts legacy ios registration and unregisters from pushy using uppercase uuid" in new registrations {
      val Some(result) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyIosRegistrationJson)))

      status(result) must equalTo(OK)
      eventually(there was one(legacyRegistrationClientWSMock).url("https://localhost/device/registrations/gia:0E980097-59FD-4047-B609-366C6D5BB1B3"))
    }

    "accepts legacy android registration and unregisters from pushy" in new registrations {
      val Some(result) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyAndroidRegistrationJson)))

      status(result) must equalTo(OK)
      eventually(there was one(legacyRegistrationClientWSMock).url("https://localhost/device/registrations/0e980097-59fd-4047-b609-366c6d5bb1b3"))
    }

    "return 204 and empty response for unregistration of udid" in new registrations {
      val Some(result) = route(app, FakeRequest(DELETE, "/registrations/ios/gia:00000000-0000-0000-0000-000000000000"))
      status(result) must equalTo(NO_CONTENT)
      contentAsString(result) must beEmpty
      there was one(registrarProviderMock).registrarFor(iOS)
      there was one(registrarProviderMock).registrarFor(any[Platform])
    }

    "return 404 for unregistration of udid that is not found" in new registrations {
      val Some(result) = route(app, FakeRequest(DELETE, "/registrations/ios/gia:F0000000-0000-0000-0000-000000000000"))

      status(result) must equalTo(NOT_FOUND)
    }

    "register with topics in registration when validation fails" in new registrations {
      topicValidatorMock.removeInvalid(topics) returns Future.successful(validatorError.left)

      val Some(result) = route(app, FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                    /("name" -> "science")
    }

    "register only with valid topics" in new registrations {
      topicValidatorMock.removeInvalid(topics) returns Future.successful((topics - footballMatchTopic).right)

      val Some(result) = route(app, FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "breaking")
                                                    /("name" -> "uk")
    }
  }

  trait registrations extends WithPlayApp {
    val legacyIosRegistrationJson =
      """
        |{
        |	"device": {
        |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
        |		"buildTier": "debug",
        |		"udid": "gia:0E980097-59FD-4047-B609-366C6D5BB1B3",
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
        |		"udid": "0e980097-59fd-4047-b609-366c6d5bb1b3",
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
        |  "userId": "83B148C0-8951-11E5-865A-222E69A460B9",
        |  "platform": "windows-mobile",
        |  "topics": [
        |    {"type": "football-match", "name": "science"},
        |    {"type": "breaking", "name": "uk"}
        |  ]
        |}
      """.stripMargin

    val footballMatchTopic = Topic(`type` = FootballMatch, name = "science")

    val topics = Set(
      footballMatchTopic,
      Topic(`type` = Breaking, name = "uk")
    )

    val legacyTopics = Set(
      Topic(`type` = Breaking, name = "uk")
    )

    val topicValidatorMock = {
      val validator = mock[TopicValidator]
      validator.removeInvalid(topics) returns Future.successful(topics.right)
      validator.removeInvalid(legacyTopics) returns Future.successful(legacyTopics.right)
      validator
    }

    val validatorError = new TopicValidatorError {
      override def reason: String = "topic validation failed"
      override def topicsQueried: Set[Topic] = topics
    }

    val notificationRegistrar = new NotificationRegistrar {
      override val providerIdentifier = "test"

      override def register(deviceId: String, registration: Registration): Future[Xor[ProviderError, RegistrationResponse]] = Future {
        RegistrationResponse(
          deviceId = "deviceAA",
          platform = WindowsMobile,
          userId = registration.udid,
          topics = registration.topics
        ).right
      }

      override def unregister(udid: UniqueDeviceIdentifier): Future[ProviderError Xor Unit] = Future {
        if (existingDeviceIds.contains(udid)) {
          ().right
        } else {
          UdidNotFound.left
        }
      }

      val existingDeviceIds = Set(UniqueDeviceIdentifier.fromString("gia:00000000-0000-0000-0000-000000000000").get)

      override def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[ProviderError Xor Paginated[StoredRegistration]] = ???

      override def findRegistrations(lastKnownChannelUri: String): Future[ProviderError Xor List[StoredRegistration]] = ???

      override def findRegistrations(udid: UniqueDeviceIdentifier): Future[ProviderError Xor Paginated[StoredRegistration]] = ???
    }

    val registrarProviderMock = {
      val provider = mock[RegistrarProvider]
      provider.registrarFor(any[Platform]) returns notificationRegistrar.right
      provider.registrarFor(any[Registration]) returns notificationRegistrar.right
      provider
    }

    val legacyRegistrationClientWSMock = mock[WSClient]

    val legacyRegistrationClientMock = {
      val conf = mock[Configuration]
      conf.legacyNotficationsEndpoint returns "https://localhost"
      val deleteRequest = {
        val request = mock[WSRequest]
        val response = mock[WSResponse]
        response.status returns 200
        request.delete() returns Future.successful(response)
      }
      legacyRegistrationClientWSMock.url(any[String]) returns deleteRequest
      new LegacyRegistrationClient(legacyRegistrationClientWSMock, conf)
    }

    override def configureComponents(context: Context): BuiltInComponents = {
      new BuiltInComponentsFromContext(context) with AppComponents {
        override lazy val topicValidator = topicValidatorMock
        override lazy val registrarProvider: RegistrarProvider = registrarProviderMock
        override lazy val appConfig = mock[Configuration]
        override lazy val legacyRegistrationClient = legacyRegistrationClientMock
      }
    }
  }
}

class RegistrarProviderMock extends RegistrarProvider {

  override def registrarFor(platform: Platform): Xor[NotificationsError, NotificationRegistrar] = new NotificationRegistrar {
    override val providerIdentifier = "test"

    override def register(deviceId: String, registration: Registration): Future[Xor[ProviderError, RegistrationResponse]] = Future {
      RegistrationResponse(
        deviceId = "deviceAA",
        platform = WindowsMobile,
        userId = registration.udid,
        topics = registration.topics
      ).right
    }

    override def unregister(udid: UniqueDeviceIdentifier): Future[ProviderError Xor Unit] = Future {
      if (existingDeviceIds.contains(udid)) {
        ().right
      } else {
        UdidNotFound.left
      }
    }

    val existingDeviceIds = Set(UniqueDeviceIdentifier.fromString("gia:00000000-0000-0000-0000-000000000000").get)

    override def findRegistrations(topic: Topic, cursor: Option[String] = None): Future[ProviderError Xor Paginated[StoredRegistration]] = ???

    override def findRegistrations(lastKnownChannelUri: String): Future[ProviderError Xor List[StoredRegistration]] = ???

    override def findRegistrations(udid: UniqueDeviceIdentifier): Future[ProviderError Xor Paginated[StoredRegistration]] = ???
  }.right

  override def withAllRegistrars[T](fn: (NotificationRegistrar) => T): List[T] = List(fn(registrarFor(WindowsMobile).toOption.get))
}
