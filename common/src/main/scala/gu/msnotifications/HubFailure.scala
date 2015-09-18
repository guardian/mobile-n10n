package gu.msnotifications

sealed trait HubFailure {
  def reason: String
}

object HubFailure {

  object HubServiceError {
    def fromXml(xml: scala.xml.Elem): Option[HubServiceError] = {
      for {
        code <- xml \ "Code"
        detail <- xml \ "Detail"
      } yield HubServiceError(detail.text, code.text.toInt)
    }.headOption
  }

  case class HubServiceError(reason: String, code: Int) extends HubFailure

  case class HubParseFailed(body: String, reason: String) extends HubFailure

  case class HubInvalidConnectionString(reason: String) extends HubFailure

  def fromResponseCode(code: Int) = code match {
    case 400 => HubServiceError("The request is malformed", code)
    case 401 => HubServiceError("Authorization failure. The access key was incorrect.", code)
    case 403 => HubServiceError("Quota exceeded or message too large; message was rejected.", code)
    case 404 => HubServiceError("No message branch at the URI.", code)
    case 413 => HubServiceError("Requested entity too large. The message size cannot be over 64Kb.", code)
    case _ => HubServiceError(s"Unknown error (code $code)", code)
  }
}
