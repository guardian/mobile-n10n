package registration.services

import cats.data.Xor
import error.NotificationsError
import models.Registration
import registration.models.LegacyRegistration

trait RegistrationConverter[T] {
  def toRegistration(from: T): NotificationsError Xor Registration
  def fromResponse(legacyRegistration: T, response: RegistrationResponse): T
}
