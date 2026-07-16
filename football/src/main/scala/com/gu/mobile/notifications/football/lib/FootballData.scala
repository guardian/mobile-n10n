package com.gu.mobile.notifications.football.lib

import com.gu.mobile.notifications.client.models.liveActitivites.LiveActivityPayload

import java.time.{LocalDate, LocalTime, ZonedDateTime}
import com.gu.mobile.notifications.football.Logging
import com.gu.mobile.notifications.football.models.{FootballMatchEvent, RawMatchData}
import com.gu.mobile.notifications.football.notificationbuilders.MatchStatusLiveActivityPayloadBuilder
import pa.{MatchDay, MatchEvent}
import play.api.libs.json.{Format, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

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
    payloadStateCheck: DynamoPayloadStateCheck,
    stage: String,
) extends Logging {

  implicit class RichMatchDay(matchDay: MatchDay) {

    private lazy val internationalTeamsForFriendlies = Set(
      "497", // England,
      "499", // Scotland,
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

    // Checks if neither team are in the internationalTeamsForFriendlies set — i.e., the match is "uncovered".
    // As long as one team id is in the set, the match should be covered.
    lazy val isUncoveredInternationalFriendly: Boolean =
      Set(matchDay.homeTeam.id, matchDay.awayTeam.id).intersect(internationalTeamsForFriendlies).isEmpty

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

  def competitionIsSupported(
      matches: List[MatchDay],
      supportedCompetitions: List[PACompetition],
  ): List[MatchDay] = {
    if (supportedCompetitions.isEmpty) {
      logger.warn("No supported competitions PA list retrieved, assuming all matches are supported")
      return matches
    } else {
      val (supported, unsupported) = matches.partition { m =>
        m.competition.exists(comp => supportedCompetitions.exists(_.id == comp.id))
      }
      if (unsupported.nonEmpty) {
        val msg = unsupported
          .map(m => s"${m.id} from competition ${m.competition.map(_.id).getOrElse("unknown")}")
          .mkString(", ")
        logger.warn(
          s"Found ${supported.size} supported matches. Skipping ${unsupported.size} matches from unsupported competitions: $msg",
        )
      }
      supported
    }
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


  /**
   * PA give 00:00 as the start time when they don't actually know the kickoff time yet,
   * so those matches are unusable and should be filtered out. Competitions in
   * `competitionsAllowingMidnightKO` are excluded from this check because a 00:00 kickoff can be
   * genuine for them (e.g. the World Cup, whose schedule spans multiple time zones and is
   * published months in advance).
   */
  private val competitionsAllowingMidnightKO = Set(
    "700", // FIFA World Cup
    "701", // FIFA World Cup qualifying
    "714", // Copa America
  )
  private[lib] def isFakeMidnightKO(matchDay: MatchDay): Boolean = {
    val isAllowlisted = matchDay.competition.exists(c => competitionsAllowingMidnightKO.contains(c.id))
    !isAllowlisted && matchDay.date.toLocalTime == LocalTime.MIDNIGHT
  }

  def matchIdsInProgress(dateTime: ZonedDateTime): Future[List[MatchDay]] = {
    def inProgress(m: MatchDay): Boolean =
      m.date.minusHours(2).isBefore(dateTime) && m.date.plusHours(4).isAfter(dateTime)

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
        .filterNot(isFakeMidnightKO)
    }
  }

  private def appendSyntheticEvents(matchDay: MatchDay, events: List[MatchEvent], stateChange: Boolean)(syntheticMatchEventGenerator: SyntheticMatchEventGenerator): Future[(MatchDay, List[MatchEvent])] = {
    val eventsWithSyntheticEvents = syntheticMatchEventGenerator.generate(events, matchDay.id, matchDay, stateChange )
    Future.successful((matchDay, eventsWithSyntheticEvents))
  }

  // Process individual match
  private[lib] def processMatch(matchDay: MatchDay): Future[Option[RawMatchData]] = {
    val matchData = for {
      // fetch current PA events timeline for match
      (_, events) <- paClient.eventsForMatch(matchDay)
      // check if the current match state is identical to the last known payload state sent.
      stateChange <- isFootballMatchStateIdentical(matchDay, events).map(!_)
      // add synthetic events to the PA events timeline, if any are generated.
      // a state change synth even will be generated for every update, but we filter out the superfluous ones in the EventConsumer.
      (_, eventsWithSyntheticEvents) <- appendSyntheticEvents(matchDay, events, stateChange)(syntheticEvents)
    } yield Some(RawMatchData(matchDay, eventsWithSyntheticEvents))

    matchData.recover { case NonFatal(exception) =>
      logger.error(s"Failed to process match ${matchDay.id}: ${exception.getMessage}", exception)
      None
    }
  }

  private[lib] def isFootballMatchStateIdentical(matchDay: MatchDay, events: List[pa.MatchEvent]): Future[Boolean] = {
    val payloadBuilder = new MatchStatusLiveActivityPayloadBuilder()
    val toFootballEvent = FootballMatchEvent.fromPaMatchEvent(matchDay.homeTeam, matchDay.awayTeam) _

    events.reverse match {
      case latestEvent :: previousEvents =>
        toFootballEvent(latestEvent) match {
          case Some(triggeringEvent) =>
            val footballMatchState = payloadBuilder.buildFootballContentState(
              triggeringEvent = triggeringEvent,
              matchInfo = matchDay,
              previousEvents = previousEvents.reverse.flatMap(toFootballEvent),
              articleId = None,
            )

            payloadStateCheck
              .isMatchStateIdentical(matchDay.id, footballMatchState)
              .recover { case NonFatal(exception) =>
                logger.error(s"Error checking for for identical football match state ${matchDay.id}: ${exception.getMessage}")
                true // assume identical
              }

          case None =>
            // latest event couldn't be converted to a FootballMatchEvent
            Future.successful(true)
        }

      case Nil =>
        Future.successful(true) // assume identical
    }
  }
}
