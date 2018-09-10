package com.gu.notifications.events

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification

import scala.collection.mutable
import scala.concurrent.ExecutionContext


class LambdaSpec(implicit ee: ExecutionEnv) extends Specification {

  val sampleJson = """{ "Records": [ { "eventVersion": "2.0", "eventSource": "aws:s3", "awsRegion": "eu-west-1", "eventTime": "2018-08-29T15:20:52.106Z", "eventName": "ObjectCreated:Put", "userIdentity": { "principalId": "AWS:201359054765:mark.richards" }, "requestParameters": { "sourceIPAddress": "77.91.250.207" }, "responseElements": { "x-amz-request-id": "CF4071DB1A155EF8", "x-amz-id-2": "f4IYn4IH8r+A+xUL6XFXyG4IGUPo0O52fX3IeZzYuHH0DUP8w6xVOsdF1ca5n3Cfo+Q2vr5L1zY=" }, "s3": { "s3SchemaVersion": "1.0", "configurationId": "Test", "bucket": { "name": "aws-mobile-event-logs-code", "ownerIdentity": { "principalId": "AMMPW2RWUX5Q9" }, "arn": "arn:aws:s3:::aws-mobile-event-logs-code" }, "object": { "key": "tests4", "size": 7, "eTag": "15d8e632a0f51bb37a1559367a61c61e", "sequencer": "005B86B9D40D3F4066" } } } ] }"""
  "TestLambda" should {
    "read s3 objects" in {

      val is: InputStream = new ByteArrayInputStream(sampleJson.getBytes(StandardCharsets.UTF_8))
      val os: OutputStream = new ByteArrayOutputStream()
      var queue: mutable.Queue[S3Event] = mutable.Queue.empty

      val processEvents = new ProcessEvents {
        override def apply(s3Event: S3Event)(implicit executionContext: ExecutionContext): Unit = queue += s3Event
      }
      new Lambda(processEvents).handleRequest(is, os, null)
      queue.toList must beEqualTo(List(S3Event(List(S3EventRecord(Some(S3EventRecordS3(
        Some(S3EventRecordS3Object(Some("tests4"))),Some(S3EventRecordS3Bucket(Some("aws-mobile-event-logs-code"))))))))))

    }
  }
}
