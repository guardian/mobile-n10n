package models

import play.api.libs.json._

sealed trait NotificationType {
  def value: String = NotificationType.toRep(this)
}

object NotificationType {
  case object BreakingNews extends NotificationType
  case object Content extends NotificationType
  case object GoalAlert extends NotificationType
  case object LiveEventAlert extends NotificationType
  case object FootballMatchStatus extends NotificationType
  case object NewsstandShard extends NotificationType

  val fromRep: Map[String, NotificationType] = Map(
    "news" -> BreakingNews,
    "content" -> Content,
    "goal" -> GoalAlert,
    "live-notification" -> LiveEventAlert,
    "football-match-status" -> FootballMatchStatus,
    "newsstand-shard" -> NewsstandShard
  )

  val toRep: Map[NotificationType, String] = fromRep.map(_.swap)

  implicit val jf = new Format[NotificationType] {
    override def writes(o: NotificationType): JsValue = JsString(o.value)

    override def reads(json: JsValue): JsResult[NotificationType] = json match {
      case JsString(value) => fromRep.get(value).map(nType => JsSuccess(nType))
        .getOrElse(JsError(s"Unknown NotificationType $value"))
      case _ => JsError("Unknown NotificationType")
    }
  }
}