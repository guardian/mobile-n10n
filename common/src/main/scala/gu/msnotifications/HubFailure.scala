package gu.msnotifications

sealed trait HubFailure

object HubFailure {

  case class HubServiceError(reason: String, code: Int) extends HubFailure

  case class HubParseFailed(body: String, reason: String) extends HubFailure

}
