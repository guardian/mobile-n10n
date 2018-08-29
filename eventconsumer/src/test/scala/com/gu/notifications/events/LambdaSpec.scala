package com.gu.notifications.events

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import org.specs2.mutable.Specification

import scala.collection.mutable


class LambdaSpec extends Specification {

  val sampleJson = """{ "Records": [ { "eventVersion": "2.0", "eventSource": "aws:s3", "awsRegion": "eu-west-1", "eventTime": "2018-08-29T15:20:52.106Z", "eventName": "ObjectCreated:Put", "userIdentity": { "principalId": "AWS:201359054765:mark.richards" }, "requestParameters": { "sourceIPAddress": "77.91.250.207" }, "responseElements": { "x-amz-request-id": "CF4071DB1A155EF8", "x-amz-id-2": "f4IYn4IH8r+A+xUL6XFXyG4IGUPo0O52fX3IeZzYuHH0DUP8w6xVOsdF1ca5n3Cfo+Q2vr5L1zY=" }, "s3": { "s3SchemaVersion": "1.0", "configurationId": "Test", "bucket": { "name": "aws-mobile-event-logs-code", "ownerIdentity": { "principalId": "AMMPW2RWUX5Q9" }, "arn": "arn:aws:s3:::aws-mobile-event-logs-code" }, "object": { "key": "tests4", "size": 7, "eTag": "15d8e632a0f51bb37a1559367a61c61e", "sequencer": "005B86B9D40D3F4066" } } } ] }"""
  val sample2 = """{ "Records": [ { "eventVersion": "2.0", "eventSource": "aws:s3", "awsRegion": "eu-west-1", "eventTime": "2018-08-29T18:17:55.737Z", "eventName": "ObjectCreated:Copy", "userIdentity": { "principalId": "AWS:201359054765:mark.richards" }, "requestParameters": { "sourceIPAddress": "77.91.250.207" }, "responseElements": { "x-amz-request-id": "CA5B17CE86A386E7", "x-amz-id-2": "Zyh3DH+8gaOGt2ycIFF5p5pg068TMbH+fK6SEGZ6SiNKVTqC3l6E17GREM0DIi7HZ9GzoAAIoWo=" }, "s3": { "s3SchemaVersion": "1.0", "configurationId": "1be9a21e-3246-445e-b4b0-b754bfcf6277", "bucket": { "name": "aws-mobile-event-logs-code", "ownerIdentity": { "principalId": "AMMPW2RWUX5Q9" }, "arn": "arn:aws:s3:::aws-mobile-event-logs-code" }, "object": { "key": "fastly/mobile-events.code.dev-guardianapis.com/2018-08-22T00%3A00%3A00.000-3Q7X3fntVz_PrJcAAAAC.log", "size": 175, "eTag": "ca5fec6c53d0310433a73fc5f68df925", "sequencer": "005B86E35339D2DCC1" } } } ] }"""
  "TestLambda" should {
    "read s3 objects" in {

      val is: InputStream = new ByteArrayInputStream(sampleJson.getBytes(StandardCharsets.UTF_8))
      val os: OutputStream = new ByteArrayOutputStream()
      var queue: mutable.Queue[S3Event] = mutable.Queue.empty
      new Lambda(event=> queue += event).handleRequest(is, os, null)
      queue.toList must beEqualTo(List(S3Event(List(S3EventRecord(Some(S3EventRecordS3(
        Some(S3EventRecordS3Object(Some("tests4"))),Some(S3EventRecordS3Bucket(Some("aws-mobile-event-logs-code"))))))))))

    }
  }
}
