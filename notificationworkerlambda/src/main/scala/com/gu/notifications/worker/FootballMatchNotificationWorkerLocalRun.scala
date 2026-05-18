package com.gu.notifications.worker

import _root_.models._
import _root_.models.TopicTypes._
import _root_.models.Importance._
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.gu.notifications.worker.tokens.ChunkedTokens
import play.api.libs.json.Json

import java.net.URI
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters._
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.LambdaLogger

/**
 * Sends a test football match status notification to a debug Android device.
 *
 * Replace deviceToken with the FCM token from your Android debug build.
 *
 * To run:
 *   sbt "project notificationworkerlambda" "runMain com.gu.notifications.worker.FootballMatchNotificationWorkerLocalRun android"
 */
object FootballMatchNotificationWorkerLocalRun extends App {

  val deviceToken = "5b8ac8c9-f3cd-4c2b-84c4-0a0696fclalb"

  val notification = FootballMatchStatusNotification(
    id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
    title = Some("Penalty Kick"),
    message = Some("Netherlands 2-2 Argentina (PT)\nArgentina win 4-3 on penalties"),
    sender = "mobile-notifications-football-lambda",
    homeTeamName = "Netherlands",
    homeTeamScore = 2,
    homeTeamMessage = "Wout Weghorst 83'\nWout Weghorst 90' +10:32",
    homeTeamId = "631",
    homeTeamRedCards = 1,
    homeTeamPenalties = Some(PenaltyScore(scored = 3, missed = 1, saved = 0)),
    awayTeamName = "Argentina",
    awayTeamScore = 2,
    awayTeamMessage = "Nahuel Molina 35'\nLionel Messi 73' pen",
    awayTeamId = "965",
    awayTeamRedCards = 0,
    awayTeamPenalties = Some(PenaltyScore(scored = 4, missed = 0, saved = 1)),
    competitionName = Some("FIFA World Cup Finals 2022"),
    roundName = Some("Round of 16"),
    venue = Some("Lusail Stadium"),
    matchId = "4356015",
    matchInfoUri = new URI("https://mobile.guardianapis.com/sport/football/matches/4356015"),
    articleUri = None,
    importance = Minor,
    topic = List(
      Topic(FootballTeam, "631"),
      Topic(FootballTeam, "965"),
      Topic(FootballMatch, "4356015")
    ),
    matchStatus = "PT",
    eventId = UUID.randomUUID().toString,
    kickOffTimestamp = Some(1670612400L),
    lineupsAvailable = Some(true),
    detailedMatchStatus = Some("PENALTIES"),
    debug = false,
    dryRun = None
  )

  val tokens = ChunkedTokens(
    notification = notification,
    range = ShardRange(0, 1),
    tokens = List(deviceToken),
    metadata = NotificationMetadata(Instant.now(), Some(1234))
  )

  val sqsEvent: SQSEvent = {
    val event = new SQSEvent()
    val sqsMessage = new SQSMessage()
    sqsMessage.setBody(Json.stringify(Json.toJson(tokens)))
    sqsMessage.setAttributes(Map("SentTimestamp" -> s"${Instant.now.toEpochMilli}").asJava)
    event.setRecords(List(sqsMessage).asJava)
    event
  }

  val localTestContext: Context = new Context {
    override def getAwsRequestId(): String = "LOCAL-RUN-REQUEST-ID"
    override def getLogGroupName(): String = ???
    override def getLogStreamName(): String = ???
    override def getFunctionName(): String = ???
    override def getFunctionVersion(): String = ???
    override def getInvokedFunctionArn(): String = ???
    override def getIdentity(): CognitoIdentity = ???
    override def getClientContext(): ClientContext = ???
    override def getRemainingTimeInMillis(): Int = ???
    override def getMemoryLimitInMB(): Int = ???
    override def getLogger(): LambdaLogger = ???
  }

  args.lastOption.map(_.toLowerCase) foreach {
    case "android" => new AndroidSender().handleChunkTokens(sqsEvent, localTestContext)
    case _         => println("invalid option, use: android")
  }
}
