package models

import org.slf4j.MDC
import play.api.libs.json._

case class Registration(
  deviceToken: DeviceToken,
  platform: Platform,
  topics: Set[Topic],
  buildTier: Option[String],
  appVersion: Option[String]
)

object Registration {
  implicit val registrationJF: Format[Registration] = {
    val base = Json.format[Registration]
    Format(
      Reads { json =>
        val invalid = (json \ "topics").asOpt[JsArray].toSeq.flatMap { arr =>
          arr.value.flatMap { v =>
            if (v.validate[Topic].isError) {
              val topicType = (v \ "type").asOpt[String].getOrElse("unknown")
              val topicName = (v \ "name").asOpt[String].getOrElse("unknown")
              Some(s"Topic Type=$topicType (Topic Id=$topicName)")
            } else None
          }
        }
        if (invalid.nonEmpty)
          MDC.put("invalidTopics", invalid.mkString(". "))
        base.reads(json)
      },
      base
    )
  }
}
