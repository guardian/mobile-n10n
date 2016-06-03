package registration.controllers

import application.WithPlayApp
import models.TopicTypes.{Breaking, FootballMatch}
import models.{Registration, Topic, WindowsMobile}
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, BuiltInComponents}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import providers.ProviderError
import registration.AppComponents
import registration.services.topic.{TopicValidator, TopicValidatorError}
import registration.services.{Configuration, NotificationRegistrar, RegistrarProvider, RegistrationResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

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

    override def configureComponents(context: Context): BuiltInComponents = {
      new BuiltInComponentsFromContext(context) with AppComponents {
        override lazy val topicValidator = topicValidatorMock
        override lazy val registrarProvider: RegistrarProvider = new RegistrarProviderMock
        override lazy val appConfig = mock[Configuration]
      }
    }
  }
}

class RegistrarProviderMock extends RegistrarProvider {

  override def registrarFor(registration: Registration): \/[String, NotificationRegistrar] = new NotificationRegistrar {
    override def register(deviceId: String, registration: Registration): Future[\/[ProviderError, RegistrationResponse]] = Future {
      RegistrationResponse(
        deviceId = "deviceAA",
        platform = WindowsMobile,
        userId = registration.userId,
        topics = registration.topics
      ).right
    }
  }.right
}
