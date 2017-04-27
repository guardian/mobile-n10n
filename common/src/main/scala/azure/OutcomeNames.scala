package azure

import azure.HubFailure.HubInvalidResponse
import cats.data.Xor
import models.JsonUtils

import scala.PartialFunction._

sealed trait OutcomeName

object OutcomeNames {
  case object AbandonedNotificationMessages extends OutcomeName
  case object BadChannel extends OutcomeName
  case object ChannelDisconnected extends OutcomeName
  case object ChannelThrottled extends OutcomeName
  case object Dropped extends OutcomeName
  case object ExpiredChannel extends OutcomeName
  case object InvalidCredentials extends OutcomeName
  case object InvalidNotificationFormat extends OutcomeName
  case object InvalidNotificationSize extends OutcomeName
  case object NoTargets extends OutcomeName
  case object PnsInterfaceError extends OutcomeName
  case object PnsServerError extends OutcomeName
  case object PnsUnavailable extends OutcomeName
  case object PnsUnreachable extends OutcomeName
  case object Skipped extends OutcomeName
  case object Success extends OutcomeName
  case object Throttled extends OutcomeName
  case object UnknownError extends OutcomeName
  case object WrongToken extends OutcomeName
}

object OutcomeName {

  import OutcomeNames._

  implicit val jf = JsonUtils.stringFormat((fromString _).andThen(_.toOption))

  def fromString(s: String): Xor[HubInvalidResponse, OutcomeName] = Xor.fromOption(condOpt(s) {
    case "AbandonedNotificationMessages" => AbandonedNotificationMessages
    case "BadChannel" => BadChannel
    case "ChannelDisconnected" => ChannelDisconnected
    case "ChannelThrottled" => ChannelThrottled
    case "Dropped" => Dropped
    case "ExpiredChannel" => ExpiredChannel
    case "InvalidCredentials" => InvalidCredentials
    case "InvalidNotificationFormat" => InvalidNotificationFormat
    case "InvalidNotificationSize" => InvalidNotificationSize
    case "NoTargets" => NoTargets
    case "PnsInterfaceError" => PnsInterfaceError
    case "PnsServerError" => PnsServerError
    case "PnsUnavailable" => PnsUnavailable
    case "PnsUnreachable" => PnsUnreachable
    case "Skipped" => Skipped
    case "Success" => OutcomeNames.Success
    case "Throttled" => Throttled
    case "UnknownError" => UnknownError
    case "WrongToken" => WrongToken
  }, HubInvalidResponse(reason = s"Invalid outcome name $s"))
}
