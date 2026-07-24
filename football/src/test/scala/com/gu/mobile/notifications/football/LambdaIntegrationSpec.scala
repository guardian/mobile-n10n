package com.gu.mobile.notifications.football

import java.time.ZonedDateTime
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, AWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBAsync, AmazonDynamoDBAsyncClientBuilder}
import com.amazonaws.services.dynamodbv2.model.{
  AttributeDefinition,
  CreateTableRequest,
  GlobalSecondaryIndex,
  KeySchemaElement,
  KeyType,
  Projection,
  ProjectionType,
  ProvisionedThroughput,
  ScalarAttributeType,
  ScanRequest,
}
import com.gu.contentapi.client.ContentApiClient
import com.gu.contentapi.client.model.SearchQuery
import com.gu.contentapi.client.model.v1.SearchResponse
import com.gu.mobile.liveactivities.event.bus.LiveActivityPusher
import com.gu.mobile.notifications.client.models.NotificationPayload
import com.gu.mobile.notifications.client.models.liveActitivites.{EventSource, LiveActivityPayload}
import com.gu.mobile.notifications.football.lib.{
  ArticleSearcher,
  DynamoPayloadStateCheck,
  FootballData,
  NotificationSender,
  PACompetition,
  PaFootballClient,
  S3DataStore,
  SyntheticMatchEventGenerator,
}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeAfterEach, Scope}
import pa.{MatchDay, MatchEvent, Parser}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.CollectionConverters._

/** End-to-end style test that drives `FootballLambda.handler` through multiple polling cycles, mimicking
 * real life PA API responses and simulating match progression.
  *
  * `FootballData` is tested; only its external boundaries are mocked:
 *   - `PaFootballClient` : mocked to return dummy PA feeds parsed from test resources (matchDay + matchEvents)
 *   - `ContentApiClient` : mocked to return an empty search response (no articles found)
 *   - `S3DataStore` : mocked to returns the dummy match's competition as a supported competition
  *   - `DynamoPayloadStateCheck` : local dynamo table spun up that can be asserted against.
 *      `Handler` classes are tested, with LiveActivityPusher and NotificationSender mocked.
 * */
class LambdaIntegrationSpec(implicit ee: ExecutionEnv) extends Specification with Mockito with BeforeAfterEach {

  sequential

  // Matches the table name FootballData/Lambda use for stage "TEST".
  val liveActivitiesTestTableName = "liveactivities-payload-TEST"
  val notificationsTestTableName = "football-notifications-TEST"

  val dynamoClient: AmazonDynamoDBAsync = AmazonDynamoDBAsyncClientBuilder
    .standard()
    .withCredentials(
      new AWSCredentialsProviderChain(new AWSCredentialsProvider {
        override def refresh(): Unit = {}
        override def getCredentials: AWSCredentials = new AWSCredentials {
          override def getAWSAccessKeyId: String = "test"
          override def getAWSSecretKey: String = "test"
        }
      }),
    )
    .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8003", Regions.EU_WEST_1.getName))
    .build

  // Create the live activities payload table (with the lastPayload-index GSI that DynamoPayloadStateCheck
  // queries) and the notifications dedupe table before each example, and tear them down afterwards, so
  // every test starts from clean tables.
  override def before: Any = {
    val liveActivitiesTable = new CreateTableRequest()
      .withTableName(liveActivitiesTestTableName)
      .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
      .withAttributeDefinitions(
        List(
          new AttributeDefinition("id", ScalarAttributeType.S),
          new AttributeDefinition("liveActivityID", ScalarAttributeType.S),
          new AttributeDefinition("ttl", ScalarAttributeType.N),
        ).asJava,
      )
      .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
      .withGlobalSecondaryIndexes(
        new GlobalSecondaryIndex()
          .withIndexName("lastPayload-index")
          .withKeySchema(
            List(
              new KeySchemaElement("liveActivityID", KeyType.HASH),
              new KeySchemaElement("ttl", KeyType.RANGE),
            ).asJava,
          )
          .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))
          .withProjection(new Projection().withProjectionType(ProjectionType.ALL)),
      )

    val notificationsTable = new CreateTableRequest()
      .withTableName(notificationsTestTableName)
      .withKeySchema(new KeySchemaElement("notificationId", KeyType.HASH))
      .withAttributeDefinitions(new AttributeDefinition("notificationId", ScalarAttributeType.S))
      .withProvisionedThroughput(new ProvisionedThroughput(1L, 1L))

    dynamoClient.createTable(liveActivitiesTable)
    dynamoClient.createTable(notificationsTable)
  }

  override def after: Any = {
    dynamoClient.deleteTable(liveActivitiesTestTableName)
    dynamoClient.deleteTable(notificationsTestTableName)
  }

  trait LambdaScope extends Scope {
    private def loadFile(file: String): String = {
      val stream = getClass.getClassLoader.getResourceAsStream(file)
      Source.fromInputStream(stream).mkString
    }

    val matchDay: MatchDay = Parser.parseMatchDay(loadFile("integration/4484328-matchday-integration.xml")).head
    val rawEvents: List[MatchEvent] =
      Parser.parseMatchEvents(loadFile("integration/match-event-feed-integration.xml")).get.events

    // Drive the handler at kick-off time so the match passes FootballData's `inProgress` window.
    val fixedDateTime: ZonedDateTime = matchDay.date

    // MUTABLE "current" poll time passed into SyntheticMatchEventGenerator to generate pre match synthtic events
    var currentPollTime: ZonedDateTime = fixedDateTime

    // Each cycle is (matchStatus, eventsToReveal, result, liveMatch, pollTime, expectedPayloadCount).
    // `expectedPayloadCount` is the total number of items expected in the live-activities table AFTER the
    // cycle has run (the table accumulates across cycles).
    // TODO assert against last payload upserted
    val matchCycles: List[(String, Int, Boolean, Boolean, ZonedDateTime, Int, Option[String])] = List(
      ("-", 0, false, false, fixedDateTime.minusMinutes(60), 1, Some("create-channel")), // pre-kick-off
      ("-", 0, false, false, fixedDateTime.minusMinutes(15), 2, Some("pre-match")), // pre-kick-off
      ("KO", 2, false, true, fixedDateTime, 3, None), // kick off
      ("FH", 4, false, true, fixedDateTime, 4, None), // first half: Magennis opener (5')
      ("FH", 6, false, true, fixedDateTime, 5, None), // Sosa equalises (15')
      ("FH", 5, false, true, fixedDateTime, 6, Some("state-change")), // Sosa goal (15') overturned by VAR, no goal
      ("FH", 8, false, true, fixedDateTime, 7, None), // Awoniyi puts Forest ahead (37')
      ("HT", 8, false, true, fixedDateTime, 8, Some("half-time")), // half-time
      ("SHS", 8, false, true, fixedDateTime, 9, Some("second-half")), // second half kicks off
      ("SHS", 10, false, true, fixedDateTime, 10, None), // Magennis levels (50')
      ("SHS", 12, false, true, fixedDateTime, 11, None), // late foul + dismissal (87')
      ("FTET", 12, false, true, fixedDateTime, 12, Some("extra-time-to-be-played")), // extra time to be played
      ("ETS", 14, false, true, fixedDateTime, 13, None), // extra time: Dominguez shot saved (120')
      ("ETFTPT", 14, false, true, fixedDateTime, 14, Some("penalties-to-be-played")), // penalties to be played
      ("PT", 22, false, true, fixedDateTime, 23, Some("penalties")), // penalty shootout + phase change
      ("FT", 22, true, false, fixedDateTime, 24, Some("end-live-activity")), // final whistle
    )

    // Real FootballData Class with PaFootballClient and S3 mocked
    val paFootballClientMock: PaFootballClient = mock[PaFootballClient]
    // Return the dummy match's competition id as a supported competition.
    val supportedCompetition: PACompetition = PACompetition(
      id = matchDay.competition.map(_.id).getOrElse("300"),
      tag = "football/fa-cup",
      fullName = "The Emirates FA Cup 24/25",
      shortName = "FA Cup",
    )
    val competitionsDataStoreMock: S3DataStore[PACompetition] = mock[S3DataStore[PACompetition]]
    competitionsDataStoreMock.fetch(any[String])(any) returns Future.successful(List(supportedCompetition))
    // Real state check backed by the local DynamoDB table created in `before`
    val payloadStateCheck: DynamoPayloadStateCheck =
      new DynamoPayloadStateCheck(dynamoClient, liveActivitiesTestTableName)
    val syntheticEvents = new SyntheticMatchEventGenerator(() => currentPollTime)
    val footballData: FootballData = new FootballData(
      paClient = paFootballClientMock,
      syntheticEvents = syntheticEvents,
      competitionsDataStore = competitionsDataStoreMock,
      payloadStateCheck = payloadStateCheck,
      stage = "TEST",
    )

    // Real ArticleSearcher with mock CAPI client
    val emptySearchResponse: SearchResponse = mock[SearchResponse]
    emptySearchResponse.results returns Nil
    val capiClientMock: ContentApiClient = mock[ContentApiClient]
    capiClientMock.getResponse(any[SearchQuery])(any, any) returns Future.successful(emptySearchResponse)
    val articleSearcher: ArticleSearcher = new ArticleSearcher(capiClientMock)

    // Real Handlers: Local Dynamo, mocked everything else.
    val configurationMock: Configuration = mock[Configuration]
    configurationMock.mapiHost returns "https://mapi.test"
    val notificationSenderMock: NotificationSender = mock[NotificationSender]
    notificationSenderMock.sendNotifications(any[List[NotificationPayload]])(any[ExecutionContext]) returns Future
      .successful(())
    val liveActivityPusherMock: LiveActivityPusher = mock[LiveActivityPusher]
    liveActivityPusherMock.pushEvents(any[List[LiveActivityPayload]], any[EventSource])(
      any[ExecutionContext],
    ) returns Future
      .successful(())

    val notificationHandler =
      new NotificationHandler(configurationMock, dynamoClient, notificationsTestTableName, notificationSenderMock)
    val liveActivityHandler = new LiveActivityHandler(dynamoClient, liveActivitiesTestTableName, liveActivityPusherMock)

    val lambda = new FootballLambda(
      footballData = footballData,
      articleSearcher = articleSearcher,
      notificationHandler = notificationHandler,
      liveActivityHandler = liveActivityHandler,
      getZonedDateTime = () => currentPollTime,
    )

  }

  "FootballLambda.handler" should {

    "correctly push expected number of live activity payloads each polling cycle for a given match" in new LambdaScope {

      val cycleResults = matchCycles.map { case (status, eventsToTake, result, live, pollTime, expectedPayloads, _) =>
        println(
          Console.BLUE + s"=== POLLING CYCLE: status='$status', eventsToTake=$eventsToTake, result=$result, live=$live, pollTime=$pollTime ===" + Console.RESET,
        )

        currentPollTime = pollTime
        val eventsSoFar = rawEvents.take(eventsToTake)

        paFootballClientMock.aroundToday(any[ZonedDateTime]) returns Future.successful(
          List(matchDay.copy(matchStatus = status, result = result, liveMatch = live)),
        )
        paFootballClientMock.eventsForMatch(any[MatchDay])(any[ExecutionContext]) returns Future.successful(
          (matchDay, eventsSoFar),
        )

        val handlerResult = lambda.handler() mustEqual "done"

        // Scan the live-activities table and assert its (accumulated) size for this cycle.
        val payloadCount = dynamoClient.scan(new ScanRequest().withTableName(liveActivitiesTestTableName)).getCount
        println(
          Console.YELLOW + s"live-activities table size after cycle: $payloadCount vs expected: $expectedPayloads" + Console.RESET,
        )

        handlerResult and
          (payloadCount.toInt aka s"live-activities table size after cycle status='$status'" mustEqual expectedPayloads)

      // TODO add an assertion for exepected last event type once the notification handler is sorted.
      }

      cycleResults.reduce(_ and _) and
        (there was exactly(matchCycles.size)(paFootballClientMock).aroundToday(any[ZonedDateTime])) and
        (there was exactly(matchCycles.size)(paFootballClientMock).eventsForMatch(any[MatchDay])(any[ExecutionContext]))
    }

    // TODO this requires filterOutStateChangeEventsNotReceivedInIsolatiom to be applied to NotificationsHandlerEventFilter
//    "correctly send expected number of push notifications each polling cycle for a given match" in new LambdaScope {
//
//      val cycleResults = matchCycles.map {
//        case (status, eventsToTake, result, live, pollTime, expectedPayloads, expectedEventType) =>
//          println(
//            Console.BLUE + s"=== POLLING CYCLE: status='$status', eventsToTake=$eventsToTake, result=$result, live=$live, pollTime=$pollTime, expectedEventType=$expectedEventType ===" + Console.RESET,
//          )
//
//          currentPollTime = pollTime
//          val eventsSoFar = rawEvents.take(eventsToTake)
//
//          paFootballClientMock.aroundToday(any[ZonedDateTime]) returns Future.successful(
//            List(matchDay.copy(matchStatus = status, result = result, liveMatch = live)),
//          )
//          paFootballClientMock.eventsForMatch(any[MatchDay])(any[ExecutionContext]) returns Future.successful(
//            (matchDay, eventsSoFar),
//          )
//
//          val handlerResult = lambda.handler() mustEqual "done"
//
//          //  Scan the live-activities table and assert its (accumulated) size for this cycle.
//          val payloadCount = dynamoClient.scan(new ScanRequest().withTableName(notificationsTestTableName)).getCount
//          println(
//            Console.YELLOW + s"push notifications table size after cycle: $payloadCount vs expected: $expectedPayloads" + Console.RESET,
//          )
//
//          // Note: the synthetic event-type assertion lives in the live-activities test above, because the
//          // synthetic type is only recoverable there (via the deterministic payload id). The notification
//          // payload doesn't carry it, and types like create-channel/state-change never become notifications.
//
//          handlerResult and
//            (payloadCount.toInt aka s"push notifications table size after cycle status='$status'" mustEqual expectedPayloads)
//      }
//
//      cycleResults.reduce(_ and _) and
//        (there was exactly(matchCycles.size)(paFootballClientMock).aroundToday(any[ZonedDateTime])) and
//        (there was exactly(matchCycles.size)(paFootballClientMock).eventsForMatch(any[MatchDay])(any[ExecutionContext]))
//    }

    "rethrow when a downstream handler fails" in new LambdaScope {
      notificationSenderMock.sendNotifications(any[List[NotificationPayload]])(any[ExecutionContext]) returns
        Future.failed(new RuntimeException("boom"))
      paFootballClientMock.aroundToday(any[ZonedDateTime]) returns Future.successful(
        List(matchDay),
      )
      paFootballClientMock.eventsForMatch(any[MatchDay])(any[ExecutionContext]) returns Future.successful(
        (matchDay, rawEvents.take(2)),
      )
      lambda.handler() must throwA[RuntimeException]
    }
  }
}
