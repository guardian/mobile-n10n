package gu.msnotifications

import play.api.data.validation.ValidationError
import play.api.libs.json._

case class WindowsRegistration(channelUri: String, userId: UserId, topics: Set[Topic]) {

  def toRaw = {
    RawWindowsRegistration(
      channelUri = channelUri,
      tags = topics.map(_.toUri) + s"User_$userId"
    )
  }

}

case class UserId(userId: String)

object UserId {
  val safeUserId = """[a-zA-Z0-9]+""".r
  implicit val readsUserId = new Reads[UserId] {
    override def reads(json: JsValue): JsResult[UserId] = json match {
      case JsString(userId@safeUserId()) => JsSuccess(UserId(userId))
      case _ => JsError(ValidationError(s"User ID not valid, must match regex '${safeUserId.regex}'"))
    }
  }
}

object WindowsRegistration {


  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  import UserId.readsUserId

  implicit val topicReads = Json.reads[Topic]

  implicit val windowsRegistrationReads = (
    (__ \ "channelUri").read[String] and
      (__ \ "topics").readNullable[Set[Topic]] and
      (__ \ "userId").read[UserId]
    ) { (channelUri, topics, userId) =>
    WindowsRegistration(
      channelUri = channelUri,
      topics = topics.toSet.flatten,
      userId = userId
    )
  }

}
