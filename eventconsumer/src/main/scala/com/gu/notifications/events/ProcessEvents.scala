package com.gu.notifications.events

import java.nio.charset.Charset
import java.util.UUID

import com.amazonaws.util.IOUtils
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.logging.log4j
import org.apache.logging.log4j.LogManager
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.util.Try

class ProcessEvents(bucketName: String) {
  val logger: log4j.Logger = LogManager.getLogger(classOf[ProcessEvents])

  /*
   * Logic
   */
  def process(s3Event: S3Event): Unit = {

    def forAllLineOfAllFiles: Iterator[String] = {
      val keys = s3Event.Records.iterator.flatMap(record => {
        for {
          s3 <- record.s3
          s3Object <- s3.`object`
          key <- s3Object.key
        } yield key
      })
      keys.flatMap(key => {
        logger.info(s"Fetching ${bucketName}/${key}")
        val s3Object = AwsClient.s3Client.getObject(bucketName, key)
        val is = s3Object.getObjectContent
        val content = try {
          IOUtils.toString(is)
        }
        finally {
          is.close()
        }
        content.lines
      })
    }

    def toRawEvent(string: String): Option[RawEvent] = {
      Json.parse(string).validate[RawEvent].asOpt
    }

    def toEvent(rawEvent: RawEvent): Option[EventsPerNotification] = {
      val parsed = URLEncodedUtils.parse(rawEvent.queryString.dropWhile(_ == '?'), Charset.forName("UTF-8"))
      val queryParams = parsed.iterator.asScala.map(kv => kv.getName -> kv.getValue).toMap

      for {
        notificationIdString <- queryParams.get("notificationId")
        platformString <- queryParams.get("platform")
        providerString <- queryParams.get("provider").orElse(Some("android"))
        notificationId <- Try(UUID.fromString(notificationIdString)).toOption
        platform <- Platform.fromString(platformString)
        provider <- Provider.fromString(providerString)
      } yield EventsPerNotification.from(notificationId, rawEvent.dateTime, platform, provider)
    }

    val events = forAllLineOfAllFiles.flatMap(toRawEvent).flatMap(toEvent).reduce(EventsPerNotification.combine)
    println(events.aggregations.toList.sortBy(-_._2.providerCounts.total).mkString("\n"))
  }
}
