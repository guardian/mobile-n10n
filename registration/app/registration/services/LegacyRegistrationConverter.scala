package registration.services

import java.util.UUID

import error.NotificationsError
import models.{UniqueDeviceIdentifier, _}
import registration.models.{LegacyRegistration, LegacyTopic}
import cats.data.Xor
import cats.implicits._

class LegacyRegistrationConverter {

  def toRegistration(legacyRegistration: LegacyRegistration): NotificationsError Xor Registration = {
    val unsupportedPlatform: NotificationsError Xor Registration =
      Xor.left(UnsupportedPlatform(legacyRegistration.device.platform))

    Platform.fromString(legacyRegistration.device.platform).fold(unsupportedPlatform) { platform =>
      Registration(
        deviceId = legacyRegistration.device.pushToken,
        platform = platform,
        // The Windows app sends a device generated guid as the userId, not a real Guardian user id so udid is equivalent
        udid = legacyRegistration.device.udid,
        topics = topics(legacyRegistration)
      ).right
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
