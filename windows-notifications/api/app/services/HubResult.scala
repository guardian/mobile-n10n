package services

import gu.msnotifications.RegistrationId

sealed trait HubResult[T]

object HubResult {

  case class Successful[T](registrationId: RegistrationId) extends HubResult[T]

  sealed trait Failure[T] extends HubResult[T]

  case class ServiceError[T](reason: String, code: Int) extends Failure[T]

  case class ServiceParseFailed[T](body: String, reason: String) extends Failure[T]

}
