package registration.controllers

import models._
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import registration.services.topic.{TopicValidator, TopicValidatorError}
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

    "time out if a registration takes longer than the configured timeout" in new DelayedRegistrationsContext {
      val Some(result) = route(app, FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))
      status(result) must equalTo(INTERNAL_SERVER_ERROR)
      contentAsString(result) must equalTo("Operation timed out")
    }

    "accepts registration and calls registrar factory" in new RegistrationsContext {
      val Some(result) = route(app, FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))
      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
      contentAsString(result) must /("topics") /# 0 /("name" -> "science")
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
        validator.removeInvalid(topics) returns Future.successful(topics.right)
        validator.removeInvalid(legacyTopics) returns Future.successful(legacyTopics.right)
        validator
      }

      fakeTopicValidator.removeInvalid(topics) returns Future.successful((topics - footballMatchTopic).right)

      val Some(result) = route(app, FakeRequest(POST, "/legacy/device/register").withJsonBody(Json.parse(legacyIosRegistrationWithFootballMatchTopicJson)))

      status(result) must equalTo(OK)
      contentAsString(result) must /("preferences") /("topics") /# 0 /("type" -> "breaking")
      contentAsString(result) must /("preferences") /("topics") /# 0 /("name" -> "uk")

      contentAsString(result) must (/("preferences") / "topics" andHave size(1))
    }

    "return 204 and empty response for unregistration of udid" in new RegistrationsContext {
      override lazy val fakeRegistrarProvider = {
        val provider = mock[RegistrarProvider]
        provider.registrarFor(any[Platform], any[Option[String]]) returns fakeNotificationRegistrar.right
        provider.registrarFor(any[Registration]) returns fakeNotificationRegistrar.right
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

    "register with topics in registration when validation fails" in new RegistrationsContext {
      override lazy val fakeTopicValidator = {
        val validator = mock[TopicValidator]
        validator.removeInvalid(topics) returns Future.successful(topics.right)
        validator.removeInvalid(legacyTopics) returns Future.successful(legacyTopics.right)
        validator
      }

      val validatorError = new TopicValidatorError {
        override def reason: String = "topic validation failed"
        override def topicsQueried: Set[Topic] = topics
      }

      fakeTopicValidator.removeInvalid(topics) returns Future.successful(validatorError.left)

      val Some(result) = route(app, FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))
      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "football-match")
      contentAsString(result) must /("topics") /# 0 /("name" -> "science")
    }

    "register only with valid topics" in new RegistrationsContext {
      override lazy val fakeTopicValidator = {
        val validator = mock[TopicValidator]
        validator.removeInvalid(topics) returns Future.successful(topics.right)
        validator.removeInvalid(legacyTopics) returns Future.successful(legacyTopics.right)
        validator
      }

      fakeTopicValidator.removeInvalid(topics) returns Future.successful((topics - footballMatchTopic).right)

      val Some(result) = route(app, FakeRequest(PUT, "/registrations/anotherRegId").withJsonBody(Json.parse(registrationJson)))
      status(result) must equalTo(OK)
      contentAsString(result) must /("topics") /# 0 /("type" -> "breaking")
      contentAsString(result) must /("topics") /# 0 /("name" -> "uk")
    }

    "return registrations for udid" in new RegistrationsContext {
      val Some(register) = route(app, FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))
      status(register) must equalTo(OK)

      val Some(result) = route(app, FakeRequest(GET, "/registrations?udid=83B148C0-8951-11E5-865A-222E69A460B9"))
      status(result) must equalTo(OK)
      contentAsString(result) must /#(0) /("userId" -> "83b148c0-8951-11e5-865a-222e69a460b9")
    }

    "return registrations for device token with platform" in new RegistrationsContext {
      val Some(register) = route(app, FakeRequest(PUT, "/registrations/someId").withJsonBody(Json.parse(registrationJson)))
      status(register) must equalTo(OK)

      val Some(result) = route(app, FakeRequest(GET, "/registrations?platform=ios&deviceToken=someId"))
      status(result) must equalTo(OK)
      contentAsString(result) must /#(0) /("platform" -> "windows-mobile")
      contentAsString(result) must /#(0) /("deviceId" -> "someId")
    }

    "return registrations for topic" in new RegistrationsContext {
      testRegistrations(registrationJson).foreach { request =>
        val Some(register) = route(app, request)
        status(register) must equalTo(OK)
      }

      val Some(result) = route(app, FakeRequest(GET, "/registrations?topic=breaking/uk"))
      status(result) must equalTo(OK)
      contentAsString(result) must /("results") /#(0) /("platform" -> "windows-mobile")
      contentAsString(result) must /("results") /#(0) /("deviceId" -> "someId8")
      contentAsString(result) must (/("results") andHave size(5))
    }

    "return registrations for topic with cursor" in new RegistrationsContext {
      testRegistrations(registrationJson).foreach { request =>
        val Some(register) = route(app, request)
        status(register) must equalTo(OK)
      }

      val Some(result) = route(app, FakeRequest(GET, "/registrations?topic=breaking/uk&cursor=test:YWJj"))
      status(result) must equalTo(OK)
      contentAsString(result) must /("results") /#(0) /("platform" -> "windows-mobile")
      contentAsString(result) must /("results") /#(0) /("deviceId" -> "someId3")
      contentAsString(result) must (/("results") andHave size(3))
    }
  }

  trait RegistrationsContext extends RegistrationsBase with withMockedWSClient {
    def testRegistrations(registrationJson: String): List[FakeRequest[AnyContentAsJson]] = {
      for { i <- (1 to 8).toList } yield {
        val body = Json.parse(registrationJson.replace("someId", s"someId$i"))
        FakeRequest(
          method = PUT,
          path = s"/registrations/someId$i"
        ).withJsonBody(body)
      }
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