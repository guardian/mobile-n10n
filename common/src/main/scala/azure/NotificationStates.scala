package azure

import azure.HubFailure.HubInvalidResponse
import cats.data.Xor
import models.JsonUtils

import scala.PartialFunction._

sealed trait NotificationState

object NotificationState {

  import NotificationStates._

  implicit val jf = JsonUtils.stringFormat((fromString _).andThen(_.toOption))

  def fromString(s: String): Xor[HubInvalidResponse, NotificationState] = Xor.fromOption(condOpt(s) {
    case "Abandoned" => Abandoned
    case "Canceled" => Canceled
    case "Completed" => Completed
    case "Enqueued" => Enqueued
    case "NoTargetFound" => NoTargetFound
    case "Processing" => Processing
    case "Scheduled" => Scheduled
    case "Unknown" => Unknown
  }, HubInvalidResponse(reason = s"Invalid notification state $s"))
}

object NotificationStates {
  case object Abandoned extends NotificationState
  case object Canceled extends NotificationState
  case object Completed extends NotificationState
  case object Enqueued extends NotificationState
  case object NoTargetFound extends NotificationState
  case object Processing extends NotificationState
  case object Scheduled extends NotificationState
  case object Unknown extends NotificationState
}