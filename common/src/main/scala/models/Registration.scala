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

        val topicsArray = (json \ "topics").asOpt[JsArray].getOrElse(JsArray())
        val (validTopics, invalidTopics) = topicsArray.value.partition(_.validate[Topic].isSuccess)

        if (invalidTopics.nonEmpty) {
          MDC.put("invalidTopics", invalidTopics.map(topicSummary).mkString(", "))
          MDC.put("validTopics", validTopics.map(topicSummary).mkString(", "))
        }

        val filteredJson = json.as[JsObject] + ("topics" -> JsArray(validTopics))
        base.reads(filteredJson)
      },
      base
    )
  }
}
