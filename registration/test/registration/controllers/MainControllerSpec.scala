package registration.controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import registration.services.topic.TopicValidator

import scala.concurrent.Future
import play.api.libs.ws.WSClient
import play.api.mvc.AnyContentAsJson

class MainControllerSpec extends PlaySpecification with JsonMatchers with Mockito {

  "Registrations controller" should {
    "responds to healtcheck" in new RegistrationsContext {
      val eventualResult = route(app, FakeRequest(GET, "/healthcheck")).get
      status(eventualResult) must equalTo(OK)
    }

    "return legacy formatted response for legacy registration" in new RegistrationsContext {
      val Some(result) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyIosRegistrationJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("device") /("platform" -> "ios")
      contentAsString(result) must /("preferences") /("receiveNewsAlerts" -> true)
    }

    "not include in invalid topics in response to legacy registration" in new RegistrationsContext {
      override lazy val fakeTopicValidator = {
        val validator = mock[TopicValidator]
        validator.removeInvalid(topics) returns Future.successful(Right(topics))
        validator.removeInvalid(legacyTopics) returns Future.successful(Right(legacyTopics))
        validator
      }

      fakeTopicValidator.removeInvalid(topics) returns Future.successful(Right(topics - footballMatchTopic))

      val Some(result) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyIosRegistrationWithFootballMatchTopicJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("preferences") /("topics") /# 0 /("type" -> "breaking")
      contentAsString(result) must /("preferences") /("topics") /# 0 /("name" -> "uk")

      contentAsString(result) must (/("preferences") / "topics" andHave size(1))
    }

    "returns 400 for a android registration without a fcmToken" in new RegistrationsContext {
      val Some(register) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyAndroidRegistrationJson)))
      status(register) must equalTo(BAD_REQUEST)
    }

    ////// NEW ENDPOINT ///////

    "new /device/register endpoint returns expected response" in new RegistrationsContext {
      val Some(result) = route(app, FakeRequest(POST, "/device/register").withJsonBody(Json.parse(newRegistrationJson)))
      println(Json.prettyPrint(Json.parse(contentAsString(result))))

      status(result) must equalTo(OK)
      contentAsString(result) must /("provider" -> "Guardian")
      contentAsString(result) must /("deviceId" ->  "TEST-TOKEN-ID")
      contentAsString(result) must /("platform" -> "ios")
      contentAsString(result) must (/("topics") andHave size(4))
    }
  }

  trait RegistrationsContext extends RegistrationsBase with withMockedWSClient {
    def testRegistrations(registrationJson: String): FakeRequest[AnyContentAsJson] = {
      val body = Json.parse(registrationJson)
      FakeRequest(
        method = POST,
        path = s"/legacy/device/register"
      ).withJsonBody(body)
    }
  }

  trait DelayedRegistrationsContext extends DelayedRegistrationsBase with withMockedWSClient

  trait withMockedWSClient { self: RegistrationsBase =>
    override val wsClient = mock[WSClient]
  }
}