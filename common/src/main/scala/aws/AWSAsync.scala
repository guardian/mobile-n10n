package aws

import java.util.concurrent.CompletableFuture
import scala.concurrent.{Future, Promise}

object AWSAsync {
  @inline
  def wrapCompletableFuture[T](cf: CompletableFuture[T]): Future[T] = {
    val p = Promise[T]()
    cf.whenComplete { (result, exception) =>
      if (exception != null) p.failure(exception)
      else p.success(result)
    }
    p.future
  }
}
