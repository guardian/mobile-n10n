package models

import org.slf4j.LoggerFactory
import play.api.libs.json._

case class Registration(
  deviceToken: DeviceToken,
  platform: Platform,
  topics: Set[Topic],
  buildTier: Option[String],
  appVersion: Option[String]
)

object Registration {
  private val logger = LoggerFactory.getLogger(classOf[Registration])

  implicit val registrationJF: Format[Registration] = {
    val base = Json.format[Registration]
    Format(
      Reads { json =>
        (json \ "topics").asOpt[JsArray].foreach { arr =>
          arr.value.foreach { v =>
            if (v.validate[Topic].isError) {
              val topicType = (v \ "type").asOpt[String].getOrElse("unknown")
              val topicName = (v \ "name").asOpt[String].getOrElse("unknown")
              logger.warn(s"Invalid topic type=$topicType topic id=$topicName")
            }
          }
        }
        base.reads(json)
      },
      base
    )
  }
}