package aws

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, PutObjectRequest, PutObjectResponse}
import exception.TopicCounterException
import models.TopicCount
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsError, JsSuccess, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait S3[T]  {

  def s3Client: S3Client
  def bucketName: String
  def path: String
  def logger: Logger

  def put(data: Seq[T])(implicit format: Format[T]): PutObjectResponse = {
    val jsonAsBytes = Json.toBytes(Json.toJson(data))
    val putObjectRequest = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(path)
      .contentType("application/json")
      .build()
    s3Client.putObject(putObjectRequest, RequestBody.fromBytes(jsonAsBytes))
  }

  def fetch()(implicit format: Format[T], executionException: ExecutionContext) : Future[List[T]] = {
    Try(parseS3Object()) match {
      case Success(list) => Future.successful(list)
      case Failure(ex) =>
        logger.error(s"Error retrieving topic registration counts from s3. Bucket: ${bucketName}, Path: ${path}")
        Future.failed(ex)
    }
  }

  private def parseS3Object()(implicit format: Format[T]) : List[T] = {
    Json.fromJson[List[T]](Json.parse(getObjectasString())) match {
      case JsSuccess(list, __) =>
        logger.debug(s"Got ${list.length} topic counts from s3")
        list
      case JsError(errors) =>
        val errorPaths = errors.map { error => error._1.toString() }.mkString(",")
        logger.error(s"Error parsing topic counts. paths: ${errorPaths}")
        throw new TopicCounterException(s"could not extract list of topic registration counts from json. Errors paths(s): $errors")
    }
  }

  private def getObjectasString(): String = {
    val getObjectRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(path)
      .build()
    s3Client.getObjectAsBytes(getObjectRequest).asUtf8String()
  }
}

class TopicCountsS3(override val s3Client: S3Client, override val bucketName: String, override val path: String) extends S3[TopicCount] {
  override def logger: Logger = LoggerFactory.getLogger(this.getClass)
}

