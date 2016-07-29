package models

import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.Try

object UniqueDeviceIdentifier {

  private def uuidFromString(s: String) = Try(UUID.fromString(s)).toOption

  def unapply(string: String): Option[(UUID, Option[String])] = {
    if (string.startsWith("gia:"))
      uuidFromString(string.stripPrefix("gia:")).map((_, Some("gia:")))
    else
      uuidFromString(string).map((_, None))
  }

  def unapply(jsValue: JsValue): Option[(UUID, Option[String])] = jsValue match {
    case JsString(uuid) => unapply(uuid)
    case _ => None
  }

  implicit val readsUserId = new Format[UniqueDeviceIdentifier] {
    override def reads(json: JsValue): JsResult[UniqueDeviceIdentifier] = json match {
      case UniqueDeviceIdentifier(uuid, prefix) => JsSuccess(UniqueDeviceIdentifier(uuid, prefix))
      case _ => JsError(ValidationError(s"User ID is not a valid UUID"))
    }

    override def writes(o: UniqueDeviceIdentifier): JsValue = JsString(o.prefix.getOrElse("") + o.id.toString)
  }
}

case class UniqueDeviceIdentifier(id: UUID, prefix: Option[String] = None)

