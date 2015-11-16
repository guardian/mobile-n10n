package models

import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.Try

object UserId {
  def unapply(string: String): Option[UUID] = Try(UUID.fromString(string)).toOption

  def unapply(jsValue: JsValue): Option[UUID] = jsValue match {
    case JsString(uuid) => Try(UUID.fromString(uuid)).toOption
    case _ => None
  }

  implicit val readsUserId = new Format[UserId] {
    override def reads(json: JsValue): JsResult[UserId] = json match {
      case UserId(uuid) => JsSuccess(UserId(uuid))
      case _ => JsError(ValidationError(s"User ID is not a valid UUID"))
    }

    override def writes(o: UserId): JsValue = JsString(o.id.toString)
  }
}

case class UserId(id: UUID)

