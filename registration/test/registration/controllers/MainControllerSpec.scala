package registration.controllers


import models.TopicTypes.{Breaking, FootballMatch}
import models.{Registration, Topic, WindowsMobile}
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import providers.ProviderError
import registration.services.topic.{TopicValidator, TopicValidatorError}
import registration.services.{NotificationRegistrar, RegistrarSupport, RegistrationResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

class MainControllerSpec extends PlaySpecification with JsonMatchers with Mockito {

  "Registrations controller" should {
    "responds to healtcheck" in new registrations {
      running(application) {
        val eventualResult = route(FakeRequest(GET, "/healthcheck")).get

        status(eventualResult) must equalTo(OK)
      }
    }

    "accepts registration and calls registrar factory" in new registrations {
      running(application) {
        val Some(result) = route(FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))

        status(result) must equalTo(OK)
        contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                      /("name" -> "science")
      }
    }

    "register with topics in registration when validatin fails" in new registrations {
      topicValidator.removeInvalid(topics) returns Future.successful(validatorError.left)
      running(application) {
        val Some(result) = route(FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))

        status(result) must equalTo(OK)
        contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                      /("name" -> "science")
      }
    }

    "register only with validated topics" in new registrations {
      topicValidator.removeInvalid(topics) returns Future.successful((topics - footballMatchTopic).right)
      running(application) {
        val Some(result) = route(FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))

        status(result) must equalTo(OK)
        contentAsString(result) must /("topics") /# 0 /("type" -> "breaking")
                                                      /("name" -> "uk")
      }
    }
  }

  trait registrations extends Scope {
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

    val topicValidator = {
      val validator = mock[TopicValidator]
      validator.removeInvalid(topics) returns Future.successful(topics.right)
      validator
    }

    val validatorError = new TopicValidatorError {
      override def reason: String = "topic validation failed"
      override def topicsQueried: Set[Topic] = topics
    }

    val application = new GuiceApplicationBuilder()
      .overrides(bind[RegistrarSupport].to[RegistrarSupportMock])
      .overrides(bind[TopicValidator].toInstance(topicValidator))
      .build()
  }
}

class RegistrarSupportMock extends RegistrarSupport {

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
