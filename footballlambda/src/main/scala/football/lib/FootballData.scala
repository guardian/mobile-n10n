package football.lib

import java.time.ZonedDateTime

import football.Logging
import football.models.RawMatchData
import org.joda.time.DateTime
import pa.MatchDay

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class EndedMatch(matchId: String, startTime: DateTime)

class FootballData(
  paClient: PaFootballClient,
  syntheticEvents: SyntheticMatchEventGenerator
) extends Logging {

  implicit class RichMatchDay(matchDay: MatchDay) {

    private lazy val internationalTeamsForFriendlies = Set(
      "497",    //England,
      "499",    //'Scotland,
      "630",    //Wales,
      "494",    //Republic of Ireland,
      "964",    //Northern Ireland,
      "999",    //Spain,
      "1678",   //Germany,
      "717",    //Italy,
      "619",    //France,
      "997",    //Belgium,
      "5539",   //Portugal,
      "1661",   //Turkey,
      "629",    //Poland,
      "716",    //Norway,
      "5845",   //Sweden,
      "986",    //Denmark,
      "5827",   //Russia,
      "965",    //Argentina,
      "23104"   //Brazil
    )

    lazy val isUncoveredInternationalFriendly: Boolean = Set(matchDay.homeTeam.id, matchDay.awayTeam.name).intersect(internationalTeamsForFriendlies).isEmpty

    lazy val isEarlyQualifyingRound: Boolean = Try(matchDay.round.roundNumber.toInt) match {
      case Success(r) => r < 3
      case _ => false
    }
  }
  
  def pollFootballData: Future[List[RawMatchData]] = {
    logger.info("Starting poll for new match events")

    val matchesData = for {
      liveMatches <- matchIdsInProgress
      md <- Batch.process(liveMatches, 5)(processMatch)
    } yield md.flatten

    matchesData andThen {
      case Success(data) =>
        logger.info(s"Finished polling with success, fetched ${data.size} matches' data")
      case Failure(e) =>
        logger.error(s"Finished polling with error ${e.getMessage}")
    }
  }

  private def matchIdsInProgress: Future[List[MatchDay]] = {
    def inProgress(m: MatchDay): Boolean =
      m.date.minusMinutes(5).isBefore(ZonedDateTime.now()) && m.date.plusHours(4).isAfter(ZonedDateTime.now())


    // unfortunately PA provide 00:00 as start date when they don't have the start date
    // so we can't do anything with these matches
    def isMidnight(matchDay: MatchDay): Boolean = {
      val localDate = matchDay.date.toLocalTime
      localDate.getHour() == 0 && localDate.getMinute() == 0
    }

    //Theres some stuff that PA don't provide data for and we want to supress alerts for these matches
    def paProvideAlerts(matchDay: MatchDay) : Boolean = {
      matchDay.competition.map {
        c => c.id match {
          //International friendly: Must involve at least one of a whitelisted set of teams
          case "721" if matchDay.isUncoveredInternationalFriendly => false
          //FA cup qualifying rounds not covererd before round 3
          case "303" if matchDay.isEarlyQualifyingRound => false
          case _ => true
        }
      }.getOrElse(false) //Shouldn't ever happen
    }

    logger.info("Retrieving today's matches from PA")
    val matches = paClient.aroundToday
    matches.map(
      _.filter(inProgress)
       .filter(paProvideAlerts)
       .filterNot(isMidnight)
    )
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
