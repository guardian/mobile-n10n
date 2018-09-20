package com.gu.notifications.events.s3

import play.api.libs.json._

case class S3EventRecordS3Object(key: Option[String])

object S3EventRecordS3Object {
  implicit val jf = Json.format[S3EventRecordS3Object]
}

case class S3EventRecordS3Bucket(name: Option[String])

object S3EventRecordS3Bucket {
  implicit val jf = Json.format[S3EventRecordS3Bucket]
}

case class S3EventRecordS3(
  `object`: Option[S3EventRecordS3Object],
  bucket: Option[S3EventRecordS3Bucket]

)

object S3EventRecordS3 {
  implicit val jf = Json.format[S3EventRecordS3]
}

case class S3EventRecord(
  s3: Option[S3EventRecordS3]
)

object S3EventRecord {
  implicit val jf = Json.format[S3EventRecord]
}

case class S3Event(Records: List[S3EventRecord])

object S3Event {
  implicit val jf = Json.format[S3Event]
}