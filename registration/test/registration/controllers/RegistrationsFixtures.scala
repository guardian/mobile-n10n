package registration.controllers

import application.WithPlayApp
import doobie.implicits._
import cats.effect.IO
import cats.syntax.all._
import com.gu.DevIdentity
import db.{DatabaseConfig, JdbcConfig, RegistrationService}
import doobie.util.transactor.Transactor
import models.Provider.Unknown
import models.TopicTypes.{Breaking, FootballMatch}
import models._
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponents, Logger, Configuration => PlayConfig}
import play.api.libs.ws.WSClient
import registration.RegistrationApplicationComponents
import registration.services.NotificationRegistrar.RegistrarResponse
import registration.services.topic.{TopicValidator, TopicValidatorError}
import registration.services._

import scala.concurrent.duration._
import scala.concurrent.Future

trait DelayedRegistrationsBase extends RegistrationsBase {
  override lazy val fakeNotificationRegistrar: NotificationRegistrar = new NotificationRegistrar {

    override val providerIdentifier: String = "test"

    override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = Future.successful {
      Thread.sleep(2000)
      Right(RegistrationResponse(
        deviceId = "deviceAA",
        platform = Android,
        topics = registration.topics,
        provider = Unknown
      ))
    }
  }
}

trait RegistrationsBase extends WithPlayApp with RegistrationsJson {

  val wsClient: WSClient

  val footballMatchTopic = Topic(`type` = FootballMatch, name = "science")

  val breakingNewsUk = Topic(`type` = Breaking, name = "uk")

  val topics = Set(footballMatchTopic, breakingNewsUk)

  val legacyTopics = Set(breakingNewsUk)

  lazy val fakeTopicValidator = new TopicValidator {
    override def removeInvalid(topics: Set[Topic]): Future[Either[TopicValidatorError, Set[Topic]]] = {
      Future.successful(Right(topics))
    }
  }

  lazy val fakeNotificationRegistrar = new NotificationRegistrar {
    override val providerIdentifier = "test"

    private var registrations = List.empty[Registration]

    override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = Future.successful {
      registrations = registration :: registrations
      Right(RegistrationResponse(
        deviceId = "deviceAA",
        platform = Android,
        topics = registration.topics,
        provider = Unknown
      ))
    }

  }


  override def configureComponents(context: Context): BuiltInComponents = {
    new RegistrationApplicationComponents(DevIdentity("notifications"), context) {
      override lazy val topicValidator = fakeTopicValidator
      override lazy val appConfig = new Configuration(PlayConfig.empty) {
        override lazy val defaultTimeout = 1.seconds
        override lazy val newsstandShards: Int = 10
      }
      override lazy val registrationDbService: RegistrationService[IO, fs2.Stream] = {
        val jdbcConfig = JdbcConfig("org.h2.Driver", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "", "")
        val transactor: Transactor[IO] = DatabaseConfig.simpleTransactor(jdbcConfig)

        val drop = sql"""DROP TABLE IF EXISTS registrations""".update.run

        val create = sql"""
        CREATE TABLE IF NOT EXISTS registrations(
          token VARCHAR NOT NULL,
          topic VARCHAR NOT NULL,
          platform VARCHAR NOT NULL,
          shard SMALLINT NOT NULL,
          lastModified TIMESTAMP WITH TIME ZONE NOT NULL,
          buildTier VARCHAR,
          PRIMARY KEY (token, topic)
        )""".update.run

        (drop, create).mapN(_ + _).transact(transactor).unsafeRunSync()
        RegistrationService(transactor)
      }
    }
  }
}

trait RegistrationsJson {
  val legacyIosRegistrationWithFootballMatchTopicJson =
    """
      |{
      |	"device": {
      |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
      |		"buildTier": "debug",
      |		"platform": "ios"
      |	},
      |	"preferences": {
      |		"edition": "UK",
      |		"topics": [
      |     {"type": "football-match", "name": "science"},
      |			{"type": "breaking", "name": "uk"}
      |		],
      |		"receiveNewsAlerts": true
      |	}
      |}
    """.stripMargin

  val legacyIosRegistrationJson =
    """
      |{
      |	"device": {
      |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
      |		"buildTier": "debug",
      |		"platform": "ios"
      |	},
      |	"preferences": {
      |		"edition": "UK",
      |		"topics": [{
      |			"type": "user-type",
      |			"name": "GuardianInternalBeta"
      |		}],
      |		"receiveNewsAlerts": true
      |	}
      |}
    """.stripMargin

  val legacyAndroidRegistrationJson =
    """
      |{
      |	"device": {
      |		"pushToken": "4027049721A496EA56A4C789B62F2C10B0380427C2A6B0CFC1DE692BDA2CC5D4",
      |		"buildTier": "debug",
      |		"platform": "android"
      |	},
      |	"preferences": {
      |		"edition": "UK",
      |		"topics": [{
      |			"type": "user-type",
      |			"name": "GuardianInternalBeta"
      |		}],
      |		"receiveNewsAlerts": true
      |	}
      |}
    """.stripMargin

  val newRegistrationJson =
    """
      |{
      |  "deviceToken": "TEST-TOKEN-ID",
      |  "platform": "ios",
      |  "buildTier": "debug",
      |  "appVersion": "1.0.0",
      |		"topics": [
      |      {
      |        "name": "4501006",
      |        "title": "Burnley vs Leeds (2025-10-18)",
      |        "type": "football-match"
      |      },
      |      {
      |        "name": "profile/jane-doe",
      |        "title": "Jane Doe",
      |        "type": "tag-contributor"
      |      },
      |      {
      |        "name": "uk-sport",
      |        "title": "UK sport notifications",
      |        "type": "breaking"
      |      },
      |      {
      |        "name": "uk",
      |        "type": "breaking"
      |      }
      |		]
      |}
    """.stripMargin

  val registrationJson =
    """
      |{
      |  "deviceId": "someId",
      |  "platform": "android",
      |  "topics": [
      |    {"type": "football-match", "name": "science"},
      |    {"type": "breaking", "name": "uk"}
      |  ]
      |}
    """.stripMargin
}
