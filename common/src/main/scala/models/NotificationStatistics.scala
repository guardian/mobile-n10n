package models

import JsonUtils._

case class NotificationStatistics(recipients: Map[Platform, Option[Int]])

object NotificationStatistics {
  import play.api.libs.json._

  implicit def platformMap[T](implicit format: Format[T]): Format[Map[Platform, T]] = new Format[Map[Platform, T]] {
    def reads(json: JsValue): JsResult[Map[Platform, T]] = {
      Json.fromJson[Map[String, T]](json) flatMap { rawMap =>
        val items = rawMap map {
          case (k, v) =>
            Json.fromJson[Platform](JsString(k)) map { _ -> v }
        }

        val errors = items.collectFirst {
          case error: JsError => error
        }

        errors getOrElse JsSuccess(items.flatMap(_.asOpt).toMap)
      }
    }

    def writes(c: Map[Platform, T]): JsValue = {
      Json.toJson(c map { case (platform, value) => platform.toString -> value })
    }
  }

  implicit val jf = Json.format[NotificationStatistics]
}