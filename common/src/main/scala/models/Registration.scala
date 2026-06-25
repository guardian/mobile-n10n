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
        def topicSummary(v: JsValue): String = {
          val t = (v \ "type").asOpt[String].getOrElse("unknown")
          val n = (v \ "name").asOpt[String].getOrElse("unknown")
          s"$t (id=$n)"
        }
        val all = (json \ "topics").asOpt[JsArray].toSeq.flatMap(_.value.map(topicSummary))
        val invalid = (json \ "topics").asOpt[JsArray].toSeq.flatMap { arr =>
          arr.value.flatMap { v =>
            if (v.validate[Topic].isError) Some(topicSummary(v)) else None
          }
        }
        if (invalid.nonEmpty) {
          MDC.put("invalidTopics", invalid.mkString(", "))
          if (all.nonEmpty) MDC.put("allTopics", all.mkString(", "))
        }
        base.reads(json)
      },
      base
    )
  }
}
