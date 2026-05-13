package com.gu.mobile.notifications.football.lib

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.util.IOUtils
import com.gu.mobile.notifications.football.Logging
import play.api.libs.json.{Format, JsError, JsSuccess, Json}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class S3DataStore[T](s3Client: AmazonS3, bucketName: String) extends Logging {
  def fetch(path: String)(implicit format: Format[T]) : Future[List[T]] = {
    Try(parseS3Object(path)) match {
      case Success(list) => Future.successful(list)
      case Failure(ex) =>
        logger.error(s"Error retrieving items from s3. Bucket: $bucketName, Path: $path")
        Future.failed(ex)
    }
  }

  private def parseS3Object(path: String)(implicit format: Format[T]) : List[T] = {
    Json.fromJson[List[T]](Json.parse(asString(s3Client.getObject(bucketName, path)))) match {
      case JsSuccess(list, __) =>
        logger.debug(s"Got ${list.length} items from s3 $bucketName, path $path")
        list
      case JsError(errors) =>
        val errorPaths = errors.map { error => error._1.toString() }.mkString(",")
        logger.error(s"Error parsing S3 items on path $path. Error path(s): $errorPaths")
        throw new Exception(s"could not extract list $path. Errors paths(s): $errors")
    }
  }

  private def asString(s3Object: S3Object): String = {
    val s3ObjectContent = s3Object.getObjectContent
    try {
      IOUtils.toString(s3ObjectContent)
    }
    finally {
      s3ObjectContent.close()
    }
  }
}
