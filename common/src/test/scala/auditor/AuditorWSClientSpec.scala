package auditor

import java.net.URL

import models.Topic
import models.TopicTypes.FootballMatch
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Play
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Results, Action}
import play.api.routing.sird._
import play.api.test.WsTestClient
import play.core.server.Server
import scala.concurrent.duration._

class AuditorWSClientSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {
  "Auditor WS Client" should {
    "query auditor host and return filtered list of topics" in {
      val topics = Set(Topic(`type` = FootballMatch, name = "barca-chelsea"))

      Server.withRouter() {
        case POST(p"/expired-topics") => Action {
          Results.Ok(Json.toJson(ExpiredTopicsResponse(topics.toList)))
        }
      } { implicit port =>
        implicit val materializer = Play.current.materializer
        WsTestClient.withClient { client =>
          val auditorWSClient = new AuditorWSClient(client)
          val auditor = Auditor(new URL(s"http://localhost:$port"))

          val filteredTopics = auditorWSClient.expiredTopics(auditor, topics)

          filteredTopics must beEqualTo(topics).awaitFor(5 seconds)
        }
      }
    }.pendingUntilFixed("problems with Materializer being closed when NettyServer tris to bind channel")

    "not query web service with empty list" in {
      val wsClient = mock[WSClient]
      val auditor = Auditor(new URL(s"http://localhost:9000"))
      val auditorWSClient = new AuditorWSClient(wsClient)

      auditorWSClient.expiredTopics(auditor, Set.empty)

      there were no(wsClient).url(anyString)
    }
  }
}
