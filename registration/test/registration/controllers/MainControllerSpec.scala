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
import registration.services.{Configuration, NotificationRegistrar, RegistrarProvider, RegistrationResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.data.Xor
import cats.implicits._
import registration.services.azure.UdidNotFound

class MainControllerSpec extends PlaySpecification with JsonMatchers with Mockito {

  "Registrations controller" should {
    "responds to healtcheck" in new registrations {
      val eventualResult = route(FakeRequest(GET, "/healthcheck")).get

      status(eventualResult) must equalTo(OK)
    }

    "accepts registration and calls registrar factory" in new registrations {
      val Some(result) = route(FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                    /("name" -> "science")
    }

    "return 204 and empty response for unregistration of udid" in new registrations {
      val Some(result) = route(FakeRequest(DELETE, "/registrations/ios/gia:00000000-0000-0000-0000-000000000000"))
      there was one(registrarProviderMock).registrarFor(iOS)
      there was one(registrarProviderMock).registrarFor(any[Platform])
      status(result) must equalTo(NO_CONTENT)
      contentAsString(result) must beEmpty
    }

    "return 404 for unregistration of udid that is not found" in new registrations {
      val Some(result) = route(FakeRequest(DELETE, "/registrations/ios/gia:F0000000-0000-0000-0000-000000000000"))

      status(result) must equalTo(NOT_FOUND)
    }

    "register with topics in registration when validation fails" in new registrations {
      topicValidatorMock.removeInvalid(topics) returns Future.successful(validatorError.left)

      val Some(result) = route(FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                    /("name" -> "science")
    }

    "register only with valid topics" in new registrations {
      topicValidatorMock.removeInvalid(topics) returns Future.successful((topics - footballMatchTopic).right)

      val Some(result) = route(FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "breaking")
                                                    /("name" -> "uk")
    }
  }

  trait registrations extends WithPlayApp {
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

    val topicValidatorMock = {
      val validator = mock[TopicValidator]
      validator.removeInvalid(topics) returns Future.successful(topics.right)
      validator
    }

    val validatorError = new TopicValidatorError {
      override def reason: String = "topic validation failed"
      override def topicsQueried: Set[Topic] = topics
    }

    val notificationRegistrar = new NotificationRegistrar {
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

    }

    val registrarProviderMock = {
      val provider = mock[RegistrarProvider]
      provider.registrarFor(any[Platform]) returns notificationRegistrar.right
      provider.registrarFor(any[Registration]) returns notificationRegistrar.right
      provider
    }

    override def configureComponents(context: Context): BuiltInComponents = {
      new BuiltInComponentsFromContext(context) with AppComponents {
        override lazy val topicValidator = topicValidatorMock
        override lazy val registrarProvider: RegistrarProvider = registrarProviderMock
        override lazy val appConfig = mock[Configuration]
      }
    }
  }
}

class RegistrarProviderMock extends RegistrarProvider {

  override def registrarFor(platform: Platform): Xor[NotificationsError, NotificationRegistrar] = new NotificationRegistrar {
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

  }.right
}
