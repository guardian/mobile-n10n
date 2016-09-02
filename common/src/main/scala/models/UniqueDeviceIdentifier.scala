package models

import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.Try

object UniqueDeviceIdentifier {

  private def uuidFromString(s: String) = Try(UUID.fromString(s)).toOption

  def fromString(s: String): Option[UniqueDeviceIdentifier] = unapply(s) map {
    case (id, prefix, uppercaseUuid) => UniqueDeviceIdentifier(id, prefix, uppercaseUuid)
  }

  def unapply(string: String): Option[(UUID, Option[String], Boolean)] = {
    val uppercaseUuid = string.toLowerCase != string
    if (string.startsWith("gia:"))
      uuidFromString(string.stripPrefix("gia:")).map((_, Some("gia:"), uppercaseUuid))
    else
      uuidFromString(string).map((_, None, uppercaseUuid))
  }

  def unapply(jsValue: JsValue): Option[(UUID, Option[String], Boolean)] = jsValue match {
    case JsString(uuid) => unapply(uuid)
    case _ => None
  }

  implicit val readsUserId = new Format[UniqueDeviceIdentifier] {
    override def reads(json: JsValue): JsResult[UniqueDeviceIdentifier] = json match {
      case UniqueDeviceIdentifier(uuid, prefix, uppercaseUuid) => JsSuccess(UniqueDeviceIdentifier(uuid, prefix, uppercaseUuid))
      case _ => JsError(ValidationError(s"User ID is not a valid UUID"))
    }

    override def writes(o: UniqueDeviceIdentifier): JsValue = JsString(o.toString)
  }
}

case class UniqueDeviceIdentifier(id: UUID, prefix: Option[String] = None, uppercaseUuid: Boolean = false) {
  
  private def idAsString = if (uppercaseUuid) id.toString.toUpperCase else id.toString
  
  override def toString: String = prefix.getOrElse("") + idAsString
}
