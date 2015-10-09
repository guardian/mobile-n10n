package controllers

import models.{Topic, Registration, UserId, WindowsMobile}
import notifications.providers.{Error, NotificationRegistrar, RegistrationResponse}
import org.specs2.specification.Scope
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import services.RegistrarSupport
import services.topic.TopicValidator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaz.\/
import scalaz.syntax.either._

class MainControllerSpec extends PlaySpecification {

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
          |  "userId": "abcd",
          |  "platform": "windows-mobile",
          |  "topics": [
          |    {"type": "content", "name": "science"}
          |  ]
          |}
        """.stripMargin

      running(application) {
        val result = route(FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson))).get

        status(result) must equalTo(OK)
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
    override def register(registration: Registration): Future[\/[Error, RegistrationResponse]] = Future {
      RegistrationResponse(
        deviceId = "deviceAA",
        platform = WindowsMobile,
        userId = UserId("idOfUser")
      ).right
    }
  }.right
}

class TopicValidatorMock extends TopicValidator {
  override def removeInvalid(topics: Set[Topic]): Future[TopicValidatorError \/ Set[Topic]] =
    Future.successful(topics.right)
}
