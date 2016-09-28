package registration.services

import cats.data.Xor
import cats.implicits._
import error.NotificationsError
import models._
import registration.models.LegacyNewsstandRegistration

class LegacyNewsstandRegistrationConverter extends RegistrationConverter[LegacyNewsstandRegistration] {

  def toRegistration(legacyRegistration: LegacyNewsstandRegistration): NotificationsError Xor Registration = {
    val udid = NewsstandUdid.fromDeviceToken(legacyRegistration.pushToken)
    Registration(
      deviceId = legacyRegistration.pushToken,
      platform = Newsstand,
      udid = udid,
      topics = Set(Topic(TopicTypes.Newsstand, "newsstand"))
    ).right
  }

  def fromResponse(legacyRegistration: LegacyNewsstandRegistration, response: RegistrationResponse): LegacyNewsstandRegistration =
    legacyRegistration
}
