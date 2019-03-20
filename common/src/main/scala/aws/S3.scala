package aws

import java.io.{ByteArrayInputStream, InputStream}

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, PutObjectResult, S3Object}
import com.amazonaws.util.IOUtils
import exception.TopicCounterException
import models.TopicCount
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsError, JsSuccess, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait S3[T]  {

  def s3Client: AmazonS3
  def bucketName: String
  def path: String
  def logger: Logger

  def put(data: Seq[T])(implicit format: Format[T]) : PutObjectResult =  {
    val (inputStream, contentLength) = jsonInputStreamAndContentLength(data)
    val metaData: ObjectMetadata = new ObjectMetadata()
    metaData.setContentType("application/json")
    metaData.setContentLength(contentLength)
    val putObjectRequest: PutObjectRequest = new PutObjectRequest(bucketName, path, inputStream, metaData)
    s3Client.putObject(putObjectRequest)
  }

  def fetch()(implicit format: Format[T], executionException: ExecutionContext) : Future[List[T]] = {
    Try(parseS3Object) match {
      case Success(list) => Future.successful(list)
      case Failure(ex) =>
        logger.error(s"Error retrieving topic registration counts from s3. Bucket: ${bucketName}, Path: ${path}")
        Future.failed(ex)
    }
  }

  private def parseS3Object()(implicit format: Format[T]) : List[T] = {
    Json.fromJson[List[T]](Json.parse(asString(s3Client.getObject(bucketName, path)))) match {
      case JsSuccess(list, __) =>
        logger.debug(s"Got ${list.length} topic counts from s3")
        list
      case JsError(errors) =>
        val errorPaths = errors.map { error => error._1.toString() }.mkString(",")
        logger.error(s"Error parsing topic counts. paths: ${errorPaths}")
        throw new TopicCounterException(s"could not extract list of topic registration counts from json. Errors paths(s): $errors")
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

  private def jsonInputStreamAndContentLength(data: Seq[T])(implicit format: Format[T]): (InputStream, Long) = {
    val jsonAsBytes  = Json.toBytes(Json.toJson(data))
    (new ByteArrayInputStream(jsonAsBytes), jsonAsBytes.length)
  }
}

class TopicCountsS3(override val s3Client: AmazonS3, override val bucketName: String, override val path: String) extends S3[TopicCount]  {
  override def logger: Logger = LoggerFactory.getLogger(this.getClass)
}

