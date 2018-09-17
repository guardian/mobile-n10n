package com.gu.notifications.events

import java.io.{BufferedReader, FileOutputStream, InputStreamReader}
import java.nio.charset.Charset
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalField
import java.util.UUID

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.model.{ListObjectsRequest, S3ObjectSummary}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import org.apache.http.client.utils.URLEncodedUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json

import collection.JavaConverters._
import scala.annotation.tailrec
import scala.io.Source
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
  }

  val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  /*
   * Logic
   */
  def process(env: Env, date: String, ids: Set[UUID]): String = {

    def allFileRequests = new Iterator[List[S3ObjectSummary]] {
      private var nextMarker: Option[String] = None
      private var firstRequest: Boolean = true

      override def hasNext: Boolean = firstRequest || nextMarker.isDefined

      override def next(): List[S3ObjectSummary] = {
        val lor = new ListObjectsRequest()
          .withBucketName("aws-mobile-event-logs-prod")
          .withPrefix(s"fastly")
          .withMarker(nextMarker.orNull)
        val list = s3Client.listObjects(lor)
        nextMarker = Option(list.getNextMarker)
        firstRequest = false
        logger.info(s"Adding ${list.getObjectSummaries.size} files to the processing list")
        println(list.getObjectSummaries.asScala.toList.map(_.getKey).mkString("\n"))
        val fileList = list.getObjectSummaries.asScala.toList.filter(f => f.getKey > "fastly/2018-09-15T15:40" && f.getKey < "fastly/2018-09-16T06:00")
        println(s"File list: ${fileList.size}")
        fileList
      }
    }

    def forAllLineOfAllFiles: Iterator[String] = {
      allFileRequests.flatMap(_.toIterator).flatMap { objectSummary =>
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
        if ids.contains(notificationId)
        platform <- Platform.fromString(platformString)
        provider <- Provider.fromString(providerString)
      } yield EventsPerNotification.from(notificationId, rawEvent.dateTime, platform, provider)
    }

    val events = forAllLineOfAllFiles.flatMap(toRawEvent).flatMap(toEvent).reduce(EventsPerNotification.combine)
    ids.foreach { id: UUID =>
      val agg = events.aggregations(id)
      logger.info(
        s"""
           |ID: $id
           |Total: ${agg.providerCounts.total}
           |iOS: ${agg.platformCounts.ios}
           |Android: ${agg.platformCounts.android}
           |Azure: ${agg.providerCounts.azure.total}
           |Azure (iOS): ${agg.providerCounts.azure.ios}
           |Azure (Android): ${agg.providerCounts.azure.android}
           |Firebase: ${agg.providerCounts.firebase.total}
           |Firebase (iOS): ${agg.providerCounts.firebase.ios}
           |Firebase (Android): ${agg.providerCounts.firebase.android}
         """.stripMargin)

      val fileContent = agg.timing
        .toList
        .sortBy(_._1.toInstant(ZoneOffset.UTC).toEpochMilli)
        .map(c => s"${dateFormat.format(c._1)}\t${c._2.azure}\t${c._2.firebase}")
        .mkString("\n")

      val fos = new FileOutputStream(s"$id.csv")
      fos.write(fileContent.getBytes)
      fos.close()
    }
    ""
  }
}

object TestIt {
  def main(args: Array[String]): Unit = {
    val uuids = args.drop(1).map(UUID.fromString).toSet
    println(Lambda.process(Env(), args(0), uuids))
  }
}
