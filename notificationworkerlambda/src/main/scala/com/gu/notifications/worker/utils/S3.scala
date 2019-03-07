package com.gu.notifications.worker.utils

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, PutObjectResult}
import models.TopicCount
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, Json}

trait S3[T] extends Logging {

  def s3Client: AmazonS3
  def bucketName: String
  def path: String

  def put(data: Seq[T])(implicit format: Format[T]) : PutObjectResult =  {
    val (inputStream, contentLength) = jsonInputStreamAndContentLength(data)
    val metaData: ObjectMetadata = new ObjectMetadata()
    metaData.setContentType("application/json")
    metaData.setContentLength(contentLength)
    val putObjectRequest: PutObjectRequest = new PutObjectRequest(bucketName, path, inputStream, metaData)
    s3Client.putObject(putObjectRequest)
  }

  private def jsonInputStreamAndContentLength(data: Seq[T])(implicit format: Format[T]): (InputStream, Long) = {
    val jsonAsBytes  = Json.toJson(data).toString().getBytes(StandardCharsets.UTF_8.name())
    (new ByteArrayInputStream(jsonAsBytes), jsonAsBytes.length)
  }
}

class TopicCountS3(override val s3Client: AmazonS3, override val bucketName: String, override val path: String) extends S3[TopicCount]  {
  override def logger: Logger = LoggerFactory.getLogger(this.getClass)
}

