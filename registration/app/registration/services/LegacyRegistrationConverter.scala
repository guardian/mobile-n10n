package registration.services

import error.{MalformattedRegistration, NotificationsError, UnsupportedPlatform}
import registration.models.LegacyTopic
import models._
import registration.models.LegacyRegistration
import cats.implicits._

class LegacyRegistrationConverter extends RegistrationConverter[LegacyRegistration] {

  def toRegistration(legacyRegistration: LegacyRegistration): Either[NotificationsError, Registration] = {

    def deviceTokenFromRegistration(platform: Platform): Either[NotificationsError, DeviceToken] = {
      (platform, legacyRegistration.device.pushToken, legacyRegistration.device.firebaseToken) match {
        //This first case is to handle lange numbers of android devices not using the latest version of the app
        //See: https://theguardian.atlassian.net/browse/MSS-609
        case (Android, _, None) => Left(MalformattedRegistration("Android device without firebase registration token"))
        case (Android, _, Some(fcmToken)) => Right(DeviceToken(fcmToken))
        case (_, Some(azureToken), _) => Right(DeviceToken(azureToken))
        case _ => Left(MalformattedRegistration("no fcm token nor azure token"))
      }
    }

    def platformFromRegistration: Either[NotificationsError, Platform] = {
      Either.fromOption(
        Platform.fromString(legacyRegistration.device.platform),
        UnsupportedPlatform(legacyRegistration.device.platform)
      )
    }

    for {
      platform <- platformFromRegistration
      deviceToken <- deviceTokenFromRegistration(platform)
    } yield Registration(
      deviceToken = deviceToken,
      platform = platform,
      topics = topics(legacyRegistration),
      buildTier = Some(legacyRegistration.device.buildTier),
      appVersion = None
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
