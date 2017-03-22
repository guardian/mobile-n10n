package auditor

import models.{Topic, TopicTypes}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import pa.{MatchDay, PaClient}
import org.mockito.Matchers.{eq => argEq}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class FootballMatchAuditorSpec(implicit ev: ExecutionEnv) extends Specification with Mockito {
  "Football match client" should {
    "query PA and return matches that have ended" in {
      val paClient = mock[PaClient]
      val matchDay = MatchDay(
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
      paClient.matchInfo(argEq("match-id-1"))(any[ExecutionContext]) returns Future.successful(matchDay.copy(id = "match-id-1", matchStatus = "FT"))
      paClient.matchInfo(argEq("match-id-2"))(any[ExecutionContext]) returns Future.successful(matchDay.copy(id = "match-id-2", matchStatus = "HT"))
      paClient.matchInfo(argEq("match-id-3"))(any[ExecutionContext]) returns Future.successful(matchDay.copy(id = "match-id-3", matchStatus = "Result"))

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
      val paClient = mock[PaClient]
      val topics = Set(
        Topic(TopicTypes.Breaking, "test-1"),
        Topic(TopicTypes.Content, "test-2"),
        Topic(TopicTypes.TagSeries, "test-3")
      )

      val auditor = FootballMatchAuditor(paClient)
      val filteredTopics = auditor.expiredTopics(topics)
      filteredTopics must beEqualTo(Set.empty).awaitFor(5 seconds)
      there were no(paClient).matchInfo(any[String])(any[ExecutionContext])
    }
  }
}
