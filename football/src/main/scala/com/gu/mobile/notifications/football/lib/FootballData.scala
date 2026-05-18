package com.gu.mobile.notifications.football.lib

import java.time.{LocalDate, ZonedDateTime}
import com.gu.mobile.notifications.football.{Configuration, Logging}
import com.gu.mobile.notifications.football.models.RawMatchData
import org.joda.time.DateTime
import pa.MatchDay
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class EndedMatch(matchId: String, startTime: DateTime)

case class PACompetition(
    id: String,
    tag: String,
    fullName: String,
    shortName: String,
    startDate: Option[LocalDate] = None,
    endDate: Option[LocalDate] = None,
)

object PACompetition {
  implicit val competitionFormat: Format[PACompetition] = Json.format[PACompetition]
}

class FootballData(
    paClient: PaFootballClient,
    syntheticEvents: SyntheticMatchEventGenerator,
    competitionsDataStore: S3DataStore[PACompetition],
    stage: String,
) extends Logging {

  implicit class RichMatchDay(matchDay: MatchDay) {

    private lazy val internationalTeamsForFriendlies = Set(
      "497", // England,
      "499", // 'Scotland,
      "630", // Wales,
      "494", // Republic of Ireland,
      "964", // Northern Ireland,
      "999", // Spain,
      "1678", // Germany,
      "717", // Italy,
      "619", // France,
      "997", // Belgium,
      "5539", // Portugal,
      "1661", // Turkey,
      "629", // Poland,
      "716", // Norway,
      "5845", // Sweden,
      "986", // Denmark,
      "5827", // Russia,
      "965", // Argentina,
      "23104", // Brazil
    )

    lazy val isUncoveredInternationalFriendly: Boolean =
      Set(matchDay.homeTeam.id, matchDay.awayTeam.name).intersect(internationalTeamsForFriendlies).isEmpty

    lazy val isEarlyQualifyingRound: Boolean = Try(matchDay.round.roundNumber.toInt) match {
      case Success(r) => r < 3
      case _          => false
    }
  }

  def pollFootballData(dateTime: ZonedDateTime): Future[List[RawMatchData]] = {
    logger.info("Starting poll for new match events")

    val matchesData = for {
      liveMatches <- matchIdsInProgress(dateTime)
      md <- Batch.process(liveMatches, 5)(processMatch)
    } yield md.flatten

    matchesData andThen {
      case Success(data) =>
        logger.info(s"Finished polling with success, fetched ${data.size} matches' data")
      case Failure(e) =>
        logger.error(s"Finished polling with error ${e.getMessage}")
    }
  }

  def matchIdsInProgress(dateTime: ZonedDateTime): Future[List[MatchDay]] = {
    def inProgress(m: MatchDay): Boolean =
      m.date.minusHours(2).isBefore(dateTime) && m.date.plusHours(4).isAfter(dateTime)

    // unfortunately PA provide 00:00 as start date when they don't have the start date
    // so we can't do anything with these matches
    def isMidnight(matchDay: MatchDay): Boolean = {
      val localDate = matchDay.date.toLocalTime
      localDate.getHour() == 0 && localDate.getMinute() == 0
    }

    // Theres some stuff that PA don't provide data for and we want to supress alerts for these matches
    def paProvideAlerts(matchDay: MatchDay): Boolean = {
      matchDay.competition
        .map { c =>
          c.id match {
            // International friendly: Must involve at least one of a whitelisted set of teams
            case "721" if matchDay.isUncoveredInternationalFriendly => false
            // FA cup qualifying rounds not covererd before round 3
            case "303" if matchDay.isEarlyQualifyingRound => false
            case _                                        => true
          }
        }
        .getOrElse(false) // Shouldn't ever happen
    }

    def competitionIsSupported(
        matches: List[MatchDay],
        supportedCompetitions: List[PACompetition],
    ): List[MatchDay] = {
      if (supportedCompetitions.isEmpty) then {
        logger.warn("No supported competitions PA list retrieved, assuming all matches are supported")
        return matches
      }
      else {
        val (supported, unsupported) = matches.partition { m =>
          m.competition.exists(comp => supportedCompetitions.exists(_.id == comp.id))
        }
        if (unsupported.nonEmpty) {
          val msg = unsupported
            .map(m => s"${m.id} from competition ${m.competition.map(_.id).getOrElse("unknown")}")
            .mkString(", ")
          logger.warn(s"Unsupported matches: $msg")
        }
        supported
      }
    }

    logger.info(s"Retrieving matches on or around $dateTime from PA")
    for {
      matches <- paClient.aroundToday(dateTime)
      competitions <- competitionsDataStore
        .fetch(s"${stage}/competition/competitions.json")
        .recover { case exception =>
          // We don't want to fail the whole process if we can't retrieve the list of competitions,
          // we'll just assume all competitions are supported and log the error
          logger.error(s"Failed to retrieve list of competitions: ${exception.getMessage}", exception)
          List.empty[PACompetition]
        }
      matchesInSupportedCompetitions = competitionIsSupported(matches, competitions)
      _ = logger.info(
        s"Retrieved ${matches.size} matches from PA, ${matchesInSupportedCompetitions.size} are in supported competitions",
      )
    } yield {
      matchesInSupportedCompetitions
        .filter(inProgress)
        .filter(paProvideAlerts)
        .filterNot(isMidnight)
    }
  }

  private def processMatch(matchDay: MatchDay): Future[Option[RawMatchData]] = {
    val matchData = for {
      (matchDay, events) <- paClient.eventsForMatch(matchDay, syntheticEvents)
    } yield Some(RawMatchData(matchDay, events))

    matchData.recover { case NonFatal(exception) =>
      logger.error(s"Failed to process match ${matchDay.id}: ${exception.getMessage}", exception)
      None
    }
  }

}
