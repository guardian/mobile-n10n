package registration.services

import error.NotificationsError
import registration.models.LegacyTopic
import models._
import registration.models.LegacyRegistration
import cats.implicits._
import models.Provider.{Azure, FCM}

class LegacyRegistrationConverter extends RegistrationConverter[LegacyRegistration] {

  def toRegistration(legacyRegistration: LegacyRegistration): Either[NotificationsError, Registration] = {

    def deviceTokenFromRegistration: Either[NotificationsError, DeviceToken] = {
      (legacyRegistration.device.pushToken, legacyRegistration.device.firebaseToken) match {
        case (Some(azureToken), Some(fcmToken)) => Right(BothTokens(azureToken, fcmToken))
        case (Some(azureToken), None) => Right(AzureToken(azureToken))
        case (None, Some(fcmToken)) => Right(FcmToken(fcmToken))
        case _ => Left(MalformattedRegistration("no fcm token nor azure token"))
      }
    }

    def platformFromRegistration: Either[NotificationsError, Platform] = {
      Either.fromOption(
        Platform.fromString(legacyRegistration.device.platform),
        UnsupportedPlatform(legacyRegistration.device.platform)
      )
    }

    def guessProvider(token: DeviceToken, platform: Platform): Option[Provider] = token match {
      case AzureToken(_) => Some(Azure)
      case FcmToken(_) => Some(FCM)
      case BothTokens(_, _) if platform == Android => Some(Azure)
      case _ => None
    }

    for {
      deviceToken <- deviceTokenFromRegistration
      platform <- platformFromRegistration
    } yield Registration(
      deviceToken = deviceToken,
      platform = platform,
      topics = topics(legacyRegistration),
      buildTier = Some(legacyRegistration.device.buildTier),
      provider = legacyRegistration.preferences.provider.orElse(guessProvider(deviceToken, platform))
    )
  }

  def fromResponse(legacyRegistration: LegacyRegistration, response: RegistrationResponse): LegacyRegistration = {
    val topics = response.topics.map { topic =>
      LegacyTopic(topic.`type`.toString, topic.name)
    }

    val preferences = legacyRegistration.preferences.copy(
      topics = Some(topics.toSeq),
      provider = Some(response.provider)
    )

    legacyRegistration.copy(preferences = preferences)
  }

  private def topics(request: LegacyRegistration): Set[Topic] = {
    val topics = for {
      topics <- request.preferences.topics.toList
      topic <- topics
      topicType <- TopicType.fromString(topic.`type`)
    } yield Topic(topicType, topic.name) // todo: check this

    val matchTopics = for {
      topics <- request.preferences.matches.toList
      topic <- topics
    } yield Topic(TopicTypes.FootballMatch, topic.matchId) // todo: check this

    val breakingTopic = if (request.preferences.receiveNewsAlerts)
      Some(Topic(TopicTypes.Breaking, request.preferences.edition.toLowerCase))
    else None

    (topics ++ matchTopics ++ breakingTopic.toList).toSet
  }
}
