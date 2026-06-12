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

  private val reads: Reads[Registration] = Reads { json =>
    for {
      deviceToken <- (json \ "deviceToken").validate[DeviceToken]
      platform    <- (json \ "platform").validate[Platform]
      buildTier   <- (json \ "buildTier").validateOpt[String]
      appVersion  <- (json \ "appVersion").validateOpt[String]
      topics      <- (json \ "topics").validate[JsArray].map { arr =>
        arr.value.flatMap { v =>
          v.validate[Topic] match {
            case JsSuccess(topic, _) => Some(topic)
            case JsError(_) =>
              val topicType = (v \ "type").asOpt[String].getOrElse("unknown")
              val topicName = (v \ "name").asOpt[String].getOrElse("unknown")
              logger.warn(s"Filtering out unrecognised topic type=$topicType topic name=$topicName ($platform $buildTier $appVersion)")
              None
          }
        }.toSet
      }
    } yield Registration(deviceToken, platform, topics, buildTier, appVersion)
  }

  private val writes: Writes[Registration] = Writes { r =>
    Json.obj(
      "deviceToken" -> r.deviceToken,
      "platform"    -> r.platform,
      "topics"      -> r.topics.toList,
      "buildTier"   -> r.buildTier,
      "appVersion"  -> r.appVersion
    )
  }

  implicit val registrationJF: Format[Registration] = Format(reads, writes)
}
