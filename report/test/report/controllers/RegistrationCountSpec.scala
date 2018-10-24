package report.controllers

import cats.data.NonEmptyList
import cats.effect.IO
import db.{PlatformCount, RegistrationService}
import models.{Topic, TopicTypes}
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{JsDefined, JsNumber}
import play.api.test.{FakeRequest, Helpers, PlaySpecification}
import report.authentication.ReportAuthAction
import play.api.{Configuration => PlayConfig}
import report.services.Configuration

class RegistrationCountSpec extends PlaySpecification with Mockito {
  "RegistrationCount controller" should {
    "return a 400 if there's not topic passed as parameters" in new RegistrationCountScope {
      registrationService.countPerPlatformForTopics(any[NonEmptyList[db.Topic]]) returns IO.pure(PlatformCount(0,0,0,0))
      val result = registrationCount.forTopics(Nil).apply(FakeRequest(GET, "/registration-count?api-key=test"))
      status(result) shouldEqual 400
      there was no(registrationService).countPerPlatformForTopics(any[NonEmptyList[db.Topic]])
    }
    "return 200 if there's a topic passed" in new RegistrationCountScope {
      registrationService.countPerPlatformForTopics(any[NonEmptyList[db.Topic]]) returns IO.pure(PlatformCount(1,1,0,0))
      val request = FakeRequest(GET, "/registration-count?api-key=test&topic=breaking/uk")
      val result = registrationCount.forTopics(List(Topic(TopicTypes.Breaking, "uk"))).apply(request)
      status(result) shouldEqual 200
      val json = contentAsJson(result)
      (json \ "total").get shouldEqual JsNumber(1)
      there was one(registrationService).countPerPlatformForTopics(any[NonEmptyList[db.Topic]])
    }
  }

  trait RegistrationCountScope extends Scope {
    val playConfig = PlayConfig(
      "notifications.api.secretKeys" -> List("test"),
      "notifications.api.electionRestrictedKeys" -> Nil,
      "notifications.api.reportsOnlyKeys" -> Nil
    )
    val configuration = new Configuration(playConfig)
    val controllerComponents = Helpers.stubControllerComponents()
    val authAction = new ReportAuthAction(configuration, controllerComponents)
    val registrationService = mock[RegistrationService[IO, fs2.Stream]]
    val registrationCount = new RegistrationCount(registrationService, controllerComponents, authAction)
  }
}
