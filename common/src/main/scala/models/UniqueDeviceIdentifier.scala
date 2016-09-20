package models

import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.Try

object UniqueDeviceIdentifier {

  private def uuidFromString(s: String) = Try(UUID.fromString(s)).toOption

  def fromString(s: String): Option[UniqueDeviceIdentifier] = {
    IosUdid.fromString(s) orElse uuidFromString(s).map(UniqueDeviceIdentifier(_))
  }

  implicit val readsUserId = new Format[UniqueDeviceIdentifier] {

    override def reads(json: JsValue): JsResult[UniqueDeviceIdentifier] = {
      val invalid = ValidationError(s"User ID is not a valid UUID")
      json.validate[String].map(UniqueDeviceIdentifier.fromString).collect(invalid) {
        case Some(udid) => udid
      }
    }

    override def writes(o: UniqueDeviceIdentifier): JsValue = JsString(o.legacyFormat)
  }

  def apply(id: UUID): UniqueDeviceIdentifier =
    new UniqueDeviceIdentifierImpl(id)
}

case class UniqueDeviceIdentifierImpl(id: UUID) extends UniqueDeviceIdentifier

trait UniqueDeviceIdentifier {

  def id: UUID

  def legacyFormat: String = id.toString
}