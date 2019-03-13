package report.data

import aws.S3
import models.TopicCount
import play.api.libs.json.Format
import utils.LruCache

import scala.concurrent.{ExecutionContext, Future}

trait DataStore[T] {
  def get()(implicit executionContext: ExecutionContext, format: Format[T]): Future[List[T]]
}

class S3DataStore[T](s3: S3[T]) extends DataStore[T] {
  override def get()(implicit executionContext: ExecutionContext, format: Format[T]): Future[List[T]] = s3.fetch
}

class CachingDataStore(dataStore: DataStore[TopicCount]) extends DataStore[TopicCount] {
  val lruCache: LruCache[TopicCount]  = new LruCache[TopicCount]((200, 1000, 3.days)
}
