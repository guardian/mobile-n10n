package registration.auditor

import models.{Topic, TopicTypes}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.Future
import scala.concurrent.duration._

class LiveblogAuditorSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {

  "A LiveblogAuditor" should {
    "ignore topics where type is not content" in new AuditorScope {
      val topics = Set(
        Topic(TopicTypes.Breaking, "test-1"),
        Topic(TopicTypes.FootballMatch, "test-2"),
        Topic(TopicTypes.TagSeries, "test-3")
      )

      val filteredTopics = auditor.expiredTopics(topics)

      auditor.expiredTopics(topics) must beEqualTo(Set.empty).awaitFor(5 seconds)
    }

    "return topics as expired where liveBloggingNow is invalid" in new AuditorScope {
      val topics = Set(
        Topic(TopicTypes.Content, "test/content/invalid-field")
      )

      auditor.expiredTopics(topics) must beEqualTo(topics).awaitFor(5 seconds)
    }

    "return topics as expired where liveBloggingNow is missing" in new AuditorScope {
      val topics = Set(
        Topic(TopicTypes.Content, "test/content/not-live-blog")
      )

      auditor.expiredTopics(topics) must beEqualTo(topics).awaitFor(5 seconds)
    }

    "return topics as expired where liveBloggingNow is false" in new AuditorScope {
      val topics = Set(
        Topic(TopicTypes.Content, "test/content/dead-blog")
      )
      auditor.expiredTopics(topics) must beEqualTo(topics).awaitFor(5 seconds)
    }

    "not return topics as expired where liveBloggingNow is true" in new AuditorScope {
      val topics = Set(
        Topic(TopicTypes.Content, "test/content/live-blog")
      )
      auditor.expiredTopics(topics) must beEqualTo(Set.empty).awaitFor(5 seconds)
    }
  }

  trait AuditorScope extends Scope {
    val config = AuditorApiConfig(url = "http://localhost:1234", apiKey = "test-key")

    val wsClient = mock[WSClient]

    def addUrl(url: String, body: String) = {
      val request = mock[WSRequest]
      val response = mock[WSResponse]
      response.json returns Json.parse(body)
      request.get().returns(Future.successful(response))
      wsClient.url(url).returns(request)
    }

    addUrl(
      url = "http://localhost:1234/test/content/invalid-field?show-fields=liveBloggingNow&api-key=test-key",
      body = """{
      |  "response": {
      |    "status": "ok",
      |    "userTier": "internal",
      |    "total": 1,
      |    "content": {
      |      "id": "test/content/live-blog",
      |      "fields": {
      |        "liveBloggingNow": "foo"
      |      }
      |    }
      |  }
      |}""".stripMargin)

    addUrl(
      url = "http://localhost:1234/test/content/live-blog?show-fields=liveBloggingNow&api-key=test-key",
      body = """{
      |  "response": {
      |    "status": "ok",
      |    "userTier": "internal",
      |    "total": 1,
      |    "content": {
      |      "id": "test/content/live-blog",
      |      "fields": {
      |        "liveBloggingNow": "true"
      |      }
      |    }
      |  }
      |}""".stripMargin)

    addUrl(
      url = "http://localhost:1234/test/content/dead-blog?show-fields=liveBloggingNow&api-key=test-key",
      body = """{
      |  "response": {
      |    "status": "ok",
      |    "userTier": "internal",
      |    "total": 1,
      |    "content": {
      |      "id": "test/content/live-blog",
      |      "fields": {
      |        "liveBloggingNow": "false"
      |      }
      |    }
      |  }
      |}""".stripMargin)

    addUrl(
      url = "http://localhost:1234/test/content/not-live-blog?show-fields=liveBloggingNow&api-key=test-key",
      body = """{
      |  "response": {
      |    "status": "ok",
      |    "userTier": "internal",
      |    "total": 1,
      |    "content": {
      |      "id": "test/content/live-blog"
      |    }
      |  }
      |}""".stripMargin)

    val auditor = LiveblogAuditor(wsClient, config)
  }
}
