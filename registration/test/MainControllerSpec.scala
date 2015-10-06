import models.{UserId, WindowsMobile, Registration}
import notifications.providers.{RegistrationResponse, Error, NotificationRegistrar}
import org.specs2.specification.Scope
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import services.{RegistrarSupport}

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
      .build()
  }

}

class RegistrarSupportMock extends RegistrarSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

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
