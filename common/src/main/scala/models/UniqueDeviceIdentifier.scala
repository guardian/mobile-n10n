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

    override def writes(o: UniqueDeviceIdentifier): JsValue = JsString(o.toString)
  }

  def apply(id: UUID): UniqueDeviceIdentifier =
    new UniqueDeviceIdentifier(id)
}

class UniqueDeviceIdentifier(val id: UUID) {

  override def toString: String = s"UniqueDeviceIdentifier($id)"

  def legacyFormat: String = id.toString

  override def equals(that: Any): Boolean = that match {
    case that: UniqueDeviceIdentifier if that.toString == this.toString => true
    case _ => false
  }

  override def hashCode: Int =
    scala.util.hashing.MurmurHash3.productHash(new Tuple1(id))
}