package registration.auditor

import models.{Topic, TopicTypes}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import pa.{Http, MatchDay, PaClient, Response}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class FootballMatchAuditorSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {
  "Football match client" should {
    "query PA and return matches that have ended" in {
      val defaultMatchDay = MatchDay(
        id =  "match-id-1",
        date =  DateTime.now,
        competition = None,
        stage = null,
        round = null,
        leg = "",
        liveMatch = true,
        result = true,
        previewAvailable = true,
        reportAvailable = true,
        lineupsAvailable = true,
        matchStatus = "FT",
        attendance = None,
        homeTeam = null,
        awayTeam = null,
        referee = None,
        venue = None,
        comments = None
      )

      val paClient = new PaClient with Http {
        override def apiKey: String = ""
        override def GET(url: String): Future[Response] = ???

        override def matchInfo(id: String)(implicit context: ExecutionContext): Future[MatchDay] = id match {
          case "match-id-1" => Future.successful(defaultMatchDay.copy(id = "match-id-1", matchStatus = "FT"))
          case "match-id-2" => Future.successful(defaultMatchDay.copy(id = "match-id-2", matchStatus = "HT"))
          case "match-id-3" => Future.successful(defaultMatchDay.copy(id = "match-id-3", matchStatus = "Result"))
        }
      }

      val topics = Set(
        Topic(TopicTypes.Breaking, "test-1"),
        Topic(TopicTypes.Content, "test-2"),
        Topic(TopicTypes.TagSeries, "test-3"),
        Topic(TopicTypes.FootballMatch, "match-id-1"),
        Topic(TopicTypes.FootballMatch, "match-id-2"),
        Topic(TopicTypes.FootballMatch, "match-id-3")
      )

      val auditor = FootballMatchAuditor(paClient)
      val filteredTopics = auditor.expiredTopics(topics)
      filteredTopics must beEqualTo(Set(Topic(TopicTypes.FootballMatch, "match-id-1"), Topic(TopicTypes.FootballMatch, "match-id-3"))).awaitFor(5 seconds)
    }

    "do not query PA if there are no football matches in topic list" in {
      val paClient = new PaClient with Http {
        override def apiKey: String = ""
        override def GET(url: String): Future[Response] = ???

        override def matchInfo(id: String)(implicit context: ExecutionContext): Future[MatchDay] = throw new Exception("This should not be called")
      }

      val topics = Set(
        Topic(TopicTypes.Breaking, "test-1"),
        Topic(TopicTypes.Content, "test-2"),
        Topic(TopicTypes.TagSeries, "test-3")
      )

      val auditor = FootballMatchAuditor(paClient)
      val filteredTopics = auditor.expiredTopics(topics)
      filteredTopics must beEqualTo(Set.empty).awaitFor(5 seconds)
    }
  }
}
