package com.gu.mobile.notifications.football.lib

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset, ZonedDateTime}

import com.gu.Logging
import com.gu.contentapi.client.ContentApiClient
import com.gu.contentapi.client.model.SearchQuery
import com.gu.mobile.notifications.football.models.{MatchDataWithArticle, RawMatchData}

import scala.concurrent.{ExecutionContext, Future}

class ArticleSearcher(capiClient: ContentApiClient) extends Logging {

  def tryToMatchWithCapiArticle(matchesData: List[RawMatchData])(implicit ec: ExecutionContext): Future[List[MatchDataWithArticle]] = {
    Batch.process(matchesData, 5) { matchData =>
      val homeTeam = matchData.matchDay.homeTeam.id
      val awayTeam = matchData.matchDay.awayTeam.id
      val matchDate = matchData.matchDay.date
      val fromInstant: Instant = ZonedDateTime.of(matchDate.getYear,
        matchDate.getMonthOfYear,
        matchDate.getDayOfMonth,
        matchDate.getHourOfDay,
        matchDate.getMinuteOfHour,
        matchDate.getSecondOfMinute,
        matchDate.getMillisOfSecond * 1000000,
        ZoneOffset.UTC
      ).toInstant

      val response = capiClient.getResponse(
        new SearchQuery()
          .fromDate(fromInstant)
          .reference(s"pa-football-team/$homeTeam,pa-football-team/$awayTeam")
          .tag("tone/minutebyminute"))
      val articleId = response.map(_.results.headOption.map(_.id))

      articleId.foreach {
        case Some(id) => logger.info(s"Attaching article $id to matchId ${matchData.matchDay.id}")
        case None => logger.info(s"No article found for matchId ${matchData.matchDay.id}")
      }

      articleId.map(matchData.withArticle)
    }
  }
}
