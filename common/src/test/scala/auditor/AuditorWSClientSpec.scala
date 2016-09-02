package auditor

import java.net.URL

import com.typesafe.config.ConfigFactory
import models.Topic
import models.TopicTypes.FootballMatch
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test.WsTestClient
import play.core.DefaultWebCommands
import play.core.server.{Server, ServerConfig}

import scala.concurrent.duration._

class AuditorWSClientSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {
  // problems with Materializer being closed when NettyServer tris to bind channel
  args(skipAll = true)

  "Auditor WS Client" should {
    "query auditor host and return filtered list of topics" in {
      val topics = Set(Topic(`type` = FootballMatch, name = "barca-chelsea"))

      val config = ServerConfig(port = Some(0), mode = Mode.Test)

      val application = new BuiltInComponentsFromContext(ApplicationLoader.Context(
        Environment.simple(path = config.rootDir, mode = config.mode),
        None, new DefaultWebCommands(), Configuration(ConfigFactory.load())
      )) {
        def router = Router.from({
          case POST(p"/expired-topics") => Action {
            Results.Ok(Json.toJson(ExpiredTopicsResponse(topics.toList)))
          }
        })
      }.application

      Server.withApplication(application, config)({ implicit port =>
        implicit val materializer = application.materializer
        WsTestClient.withClient { client =>
          val auditorWSClient = new AuditorWSClient(client)
          val auditor = Auditor(new URL(s"http://localhost:$port"))

          val filteredTopics = auditorWSClient.expiredTopics(auditor, topics)

          filteredTopics must beEqualTo(topics).awaitFor(5 seconds)
        }
      })
    }

    "not query web service with empty list" in {
      val wsClient = mock[WSClient]
      val auditor = Auditor(new URL(s"http://localhost:9000"))
      val auditorWSClient = new AuditorWSClient(wsClient)

      auditorWSClient.expiredTopics(auditor, Set.empty)

      there were no(wsClient).url(anyString)
    }
  }
}
