package com.gu.notifications.events

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.Charset
import java.util.UUID

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import org.apache.http.client.utils.URLEncodedUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json

import collection.JavaConverters._
import scala.util.Try


case class Env(app: String, stack: String, stage: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV")
  )
}

object Lambda {

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("mobile"),
    DefaultAWSCredentialsProviderChain.getInstance)

  val s3Client: AmazonS3 = AmazonS3Client.builder()
    .withCredentials(credentials)
    .withRegion("eu-west-1")
    .build

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /*
   * This is your lambda entry point
   */
  def handler(context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    logger.info(process(env))
  }

  /*
   * Logic
   */
  def process(env: Env): String = {
    def forAllLineOfAllFiles: Iterator[String] = {
      val list = s3Client.listObjects("aws-mobile-logs", "fastly/events.mobile.guardianapis.com")
      list.getObjectSummaries.asScala.toIterator.flatMap { objectSummary =>
        logger.info(s"Fetching ${objectSummary.getBucketName}/${objectSummary.getKey}")
        val s3Object = s3Client.getObject(objectSummary.getBucketName, objectSummary.getKey)
        val b = new BufferedReader(new InputStreamReader(s3Object.getObjectContent))
        b.lines().iterator.asScala
      }
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

    val rawEvents = forAllLineOfAllFiles.flatMap(toRawEvent)
    logger.info(rawEvents.hasNext.toString)
    val events = rawEvents.flatMap(toEvent).reduce(EventsPerNotification.combine)
    events.toString
  }
}

object TestIt {
  def main(args: Array[String]): Unit = {
    println(Lambda.process(Env()))
  }
}
