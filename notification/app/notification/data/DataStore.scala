package notification.data

import aws.S3
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Format
import utils.LruCache

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

trait DataStore[T] {
  def get()(implicit executionContext: ExecutionContext, format: Format[T]): Future[List[T]]
}

class S3DataStore[T](s3: S3[T]) extends DataStore[T] {
  override def get()(implicit executionContext: ExecutionContext, format: Format[T]): Future[List[T]] = s3.fetch()
}

class CachingDataStore[T](dataStore: DataStore[T]) extends DataStore[T] {
  //There's only ever one list of topic counts
  private val lruCache: LruCache[List[T]] = new LruCache[List[T]](1, 1, 5 minutes)
  private val cacheKey = "topicCounts"
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  
  override def get()(implicit executionContext: ExecutionContext, format: Format[T]): Future[List[T]] = lruCache(cacheKey) {
    logger.info("Retrieving topic counts data")
    dataStore.get()
  } 
}
