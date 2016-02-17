package registration.controllers

import java.util.UUID

import models.TopicTypes.FootballMatch
import models.{Registration, Topic, UserId, WindowsMobile}
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import providers.ProviderError
import registration.services.{RegistrationResponse, NotificationRegistrar, RegistrarSupport}
import registration.services.topic.TopicValidator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

class MainControllerSpec extends PlaySpecification with JsonMatchers{

  "Registrations controller" should {
    "responds to healtcheck" in new registrations {
      running(application) {
        val eventualResult = route(FakeRequest(GET, "/healthcheck")).get

        status(eventualResult) must equalTo(OK)
      }
    }

    "accepts registration and calls registrar factory" in new registrations {
      val registrationJson =
        """
          |{
          |  "deviceId": "someId",
          |  "userId": "83B148C0-8951-11E5-865A-222E69A460B9",
          |  "platform": "windows-mobile",
          |  "topics": [
          |    {"type": "football-match", "name": "science"}
          |  ]
          |}
        """.stripMargin

      running(application) {
        val Some(result) = route(FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))

        status(result) must equalTo(OK)
        contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
                                                      /("name" -> "science")
      }
    }
  }

  trait registrations extends Scope {
    val application = new GuiceApplicationBuilder()
      .overrides(bind[RegistrarSupport].to[RegistrarSupportMock])
      .overrides(bind[TopicValidator].to[TopicValidatorMock])
      .build()
  }

}

class RegistrarSupportMock extends RegistrarSupport {

  override def registrarFor(registration: Registration): \/[String, NotificationRegistrar] = new NotificationRegistrar {
    override def register(deviceId: String, registration: Registration): Future[\/[ProviderError, RegistrationResponse]] = Future {
      RegistrationResponse(
        deviceId = "deviceAA",
        platform = WindowsMobile,
        userId = UserId(UUID.fromString("83B148C0-8951-11E5-865A-222E69A460B9")),
        topics = Set(Topic(`type` = FootballMatch, name = "match-in-response"))
      ).right
    }
  }.right
}

class TopicValidatorMock extends TopicValidator {
  override def removeInvalid(topics: Set[Topic]): Future[TopicValidatorError \/ Set[Topic]] =
    Future.successful(topics.right)
}
