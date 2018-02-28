package registration.services

import error.NotificationsError
import models.Registration

trait RegistrationConverter[T] {
  def toRegistration(from: T): Either[NotificationsError, Registration]
  def fromResponse(legacyRegistration: T, response: RegistrationResponse): T
}
