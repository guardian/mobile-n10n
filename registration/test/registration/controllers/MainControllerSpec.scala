package registration.controllers

import models._
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import registration.services.topic.TopicValidator
import registration.services._

import scala.concurrent.Future
import cats.implicits._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
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

    "return 204 and empty response for unregistration of udid" in new RegistrationsContext {
      override lazy val fakeRegistrarProvider = {
        val provider = mock[RegistrarProvider]
        provider.registrarFor(any[Platform], any[Option[String]]) returns Right(fakeNotificationRegistrar)
        provider.registrarFor(any[Registration]) returns Right(fakeNotificationRegistrar)
        provider
      }

      val Some(result) = route(app, FakeRequest(DELETE, "/registrations/ios/gia:00000000-0000-0000-0000-000000000000"))
      status(result) must equalTo(NO_CONTENT)
      contentAsString(result) must beEmpty
      there was one(fakeRegistrarProvider).registrarFor(iOS, None)
      there was one(fakeRegistrarProvider).registrarFor(any[Platform], any[Option[String]])
    }

    "return 404 for unregistration of udid that is not found" in new RegistrationsContext {
      val Some(result) = route(app, FakeRequest(DELETE, "/registrations/ios/gia:F0000000-0000-0000-0000-000000000000"))
      status(result) must equalTo(NOT_FOUND)
    }

    "return registrations for topic" in new RegistrationsContext {
      val Some(register) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyIosRegistrationWithFootballMatchTopicJson)))
      status(register) must equalTo(OK)

      val Some(result) = route(app, FakeRequest(GET, "/registrations?topic=breaking/uk"))
      status(result) must equalTo(OK)
      contentAsString(result) must /("results") /#(0) /("platform" -> "ios")
      contentAsString(result) must /("results") /#(0) /("deviceId" -> "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4")
      contentAsString(result) must (/("results") andHave size(1))
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

    val deleteRequest = {
      val request = mock[WSRequest]
      val response = mock[WSResponse]
      response.status returns 200
      request.delete() returns Future.successful(response)
    }
    wsClient.url(any[String]) returns deleteRequest
  }
}