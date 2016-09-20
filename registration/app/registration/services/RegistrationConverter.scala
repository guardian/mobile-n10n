package registration.services

import cats.data.Xor
import error.NotificationsError
import models.Registration

trait RegistrationConverter[T] {
  def toRegistration(from: T): NotificationsError Xor Registration
}
