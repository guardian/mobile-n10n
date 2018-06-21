package registration.services

import error.NotificationsError
import registration.models.LegacyTopic
import models._
import registration.models.LegacyRegistration
import cats.implicits._

class LegacyRegistrationConverter extends RegistrationConverter[LegacyRegistration] {

  def toRegistration(legacyRegistration: LegacyRegistration): Either[NotificationsError, Registration] = {
    val unsupportedPlatform: Either[NotificationsError, Registration] =
      Left(UnsupportedPlatform(legacyRegistration.device.platform))

    Platform.fromString(legacyRegistration.device.platform).fold(unsupportedPlatform) { platform =>
      Right(Registration(
        deviceId = legacyRegistration.device.pushToken,
        platform = platform,
        // The Windows app sends a device generated guid as the userId, not a real Guardian user id so udid is equivalent
        topics = topics(legacyRegistration),
        buildTier = Some(legacyRegistration.device.buildTier)
      ))
    }
  }

  def fromResponse(legacyRegistration: LegacyRegistration, response: RegistrationResponse): LegacyRegistration = {
    val topics = response.topics.map { topic =>
      LegacyTopic(topic.`type`.toString, topic.name)
    }

    val preferences = legacyRegistration.preferences.copy(topics = Some(topics.toSeq))

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
