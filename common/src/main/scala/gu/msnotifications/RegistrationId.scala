package gu.msnotifications

case class RegistrationId(registrationId: String)

object RegistrationId {
  def fromString(registrationId: String): Either[String, RegistrationId] =
    Right(RegistrationId(registrationId))
}