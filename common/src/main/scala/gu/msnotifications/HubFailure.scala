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

}
