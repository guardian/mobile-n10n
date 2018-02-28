package registration.services

import cats.implicits._
import error.NotificationsError
import models._
import registration.models.LegacyNewsstandRegistration

class LegacyNewsstandRegistrationConverter extends RegistrationConverter[LegacyNewsstandRegistration] {

  def toRegistration(legacyRegistration: LegacyNewsstandRegistration): Either[NotificationsError, Registration] = {
    val udid = NewsstandUdid.fromDeviceToken(legacyRegistration.pushToken)
    Right(Registration(
      deviceId = legacyRegistration.pushToken,
      platform = Newsstand,
      udid = udid,
      topics = Set(Topic(TopicTypes.Newsstand, "newsstand")),
      buildTier = None
    ))
  }

  def fromResponse(legacyRegistration: LegacyNewsstandRegistration, response: RegistrationResponse): LegacyNewsstandRegistration =
    legacyRegistration
}
