package com.gu.liveactivities.util

import java.util.concurrent.CompletableFuture
import scala.concurrent.{Future, Promise}
import scala.util.Success

object FutureUtils {
  implicit class RichJavaFuture[T](javaFuture: CompletableFuture[T]) {

    def toScala: Future[T] = {
      val promise = Promise[T]()
      javaFuture.whenComplete((result, err) => {
        if (result == null) {
          promise.failure(err)
        } else {
          promise.complete(Success(result))
        }
      })
      promise.future
    }
  }
}
