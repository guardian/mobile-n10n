package models

import play.api.data.validation.ValidationError
import play.api.libs.json._

object UserId {
  val safeUserId = """[a-zA-Z0-9]+""".r
  implicit val readsUserId = new Format[UserId] {
    override def reads(json: JsValue): JsResult[UserId] = json match {
      case JsString(userId @ safeUserId()) => JsSuccess(UserId(userId))
      case _ => JsError(ValidationError(s"User ID not valid, must match regex '${safeUserId.regex}'"))
    }

    override def writes(o: UserId): JsValue = JsString(o.userId)
  }
}

case class UserId(userId: String)

