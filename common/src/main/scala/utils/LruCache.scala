package utils

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}

class LruCache[V](
  initialCapacity: Int,
  maxCapacity: Long,
  timeToLive: FiniteDuration
) {

  private class Entry[T](val promise: Promise[T], val deadline: Deadline) {
    def isAlive() : Boolean = deadline.hasTimeLeft

    def future = promise.future
  }

  private val store = new ConcurrentLinkedHashMap.Builder[Any, Entry[V]]
    .initialCapacity(initialCapacity)
    .maximumWeightedCapacity(maxCapacity)
    .build()

  def get(key: Any) : Option[Future[V]] = {
    store.get(key) match {
      case null => None
      case entry if entry.isAlive =>
        Some(entry.future)
      case entry =>
        if(store.remove(key, entry)) None
        else get(key)
    }
  }

  def apply(key: Any)(generateValue: => Future[V])(implicit ec: ExecutionContext): Future[V] = {
    def insert = {
      val newEntry = new Entry(Promise[V](), timeToLive.fromNow)
      val valueFuture =
        store.put(key, newEntry) match {
          case null =>
            generateValue
          case entry => if (entry.isAlive) {
            entry.future
          } else {
            generateValue
          }
        }
      valueFuture.onComplete { value =>
        newEntry.promise.tryComplete(value)
        //Remove any cached entries that were exceptions
        if(value.isFailure) store.remove(key, newEntry)
      }
      newEntry.promise.future
    }

    store.get(key) match {
      case null =>
        insert
      case entry if entry.isAlive =>
        entry.future
      case entry => insert
    }
  }

  def remove(key: Any) = store.remove(key) match {
    case null => None
    case entry if entry.isAlive() => Some(entry.future)
    case entry => None
  }
}

object LruCache {
  def apply[V](
    initialCapacity: Int = 16,
    maxCapacity: Int = 256,
    timeToLive: FiniteDuration = 1.hour
  ): LruCache[V] =
    new LruCache[V](initialCapacity, maxCapacity, timeToLive)
}