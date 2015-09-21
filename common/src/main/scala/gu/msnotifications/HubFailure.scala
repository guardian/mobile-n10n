package gu.msnotifications

import play.api.libs.ws.WSResponse

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

    def fromWSResponse(response: WSResponse) = HubServiceError(
      reason = response.statusText,
      code = response.status
    )
  }

  case class HubServiceError(reason: String, code: Int) extends HubFailure

  object HubParseFailed {
    def invalidXml(body: String) = HubParseFailed(
      body = body,
      reason = "Failed to find any XML"
    )
  }
  case class HubParseFailed(body: String, reason: String) extends HubFailure

  case class HubInvalidConnectionString(reason: String) extends HubFailure
}
