package com.gu.notifications.events

import java.net.URLDecoder
import java.nio.charset.{Charset, StandardCharsets}
import java.util.UUID

import com.amazonaws.util.IOUtils
import com.gu.notifications.events.model.{Platform, Provider}
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.logging.log4j
import org.apache.logging.log4j.LogManager
import play.api.libs.json._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait S3EventProcessor {
  def process(s3Event: S3Event)(implicit executionContext: ExecutionContext): EventsPerNotification
}

class S3EventProcessorImpl extends S3EventProcessor {
  val logger: log4j.Logger = LogManager.getLogger(classOf[S3EventProcessor])

  def process(s3Event: S3Event)(implicit executionContext: ExecutionContext): EventsPerNotification = {
    def forAllLineOfAllFiles: Seq[String] = {
      val keys = s3Event.Records.seq.flatMap(record => {
        for {
          s3 <- record.s3
          s3Object <- s3.`object`
          s3Bucket <- s3.bucket
          key <- s3Object.key
          bucket <- s3Bucket.name
          decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8.name())
        } yield (bucket, decodedKey)
      })
      keys.flatMap { case (bucketName, key) => {
        val s3Object = AwsClient.s3Client.getObject(bucketName, key)
        val is = s3Object.getObjectContent
        val content = try {
          IOUtils.toString(is)
        }
        finally {
          is.close()
        }
        content.lines
      }
      }
    }

    def toRawEvent(string: String): Option[RawEvent] = Try {
      Json.parse(string).validate[RawEvent].get
    } match {
      case Success(value) => Some(value)
      case Failure(t) => logger.warn(s"Could not convert event into RawEvent: $string",t)
        None
    }

    def toEvent(rawEvent: RawEvent): Option[EventsPerNotification] = {
      val parsed = URLEncodedUtils.parse(rawEvent.queryString.dropWhile(_ == '?'), Charset.forName("UTF-8"))
      val queryParams = parsed.iterator.asScala.map(kv => kv.getName -> kv.getValue).toMap
      val maybeEventsPerNotification = for {
        notificationIdString <- queryParams.get("notificationId")
        platformString <- queryParams.get("platform")
        providerString <- queryParams.get("provider").orElse(Some("android"))
        notificationId <- Try(UUID.fromString(notificationIdString)).toOption
        platform <- Platform.fromString(platformString)
        provider <- Provider.fromString(providerString)
      } yield EventsPerNotification.from(notificationId, rawEvent.dateTime, platform, provider)
      if(maybeEventsPerNotification.isEmpty) {
        logger.warn(s"Could not read $rawEvent")
      }
      maybeEventsPerNotification
    }

    val eventsPerNotificationPerS3File = forAllLineOfAllFiles.flatMap(toRawEvent).flatMap(toEvent)
    if(eventsPerNotificationPerS3File.isEmpty) {
      EventsPerNotification(Map.empty)
    }
    else {
      eventsPerNotificationPerS3File.reduce(EventsPerNotification.combine)
    }
  }


}
