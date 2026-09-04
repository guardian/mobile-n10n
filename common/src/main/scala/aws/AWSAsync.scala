package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import java.util.concurrent.{CompletableFuture, Future => JFuture}
import scala.concurrent.{Future, Promise}

object AWSAsync {
  private def promiseToAsyncHandler[Request <: AmazonWebServiceRequest, Result](p: Promise[Result]) =
    new AsyncHandler[Request, Result] {
      override def onError(exception: Exception): Unit = { p.failure(exception); () }
      override def onSuccess(request: Request, result: Result): Unit = { p.success(result); () }
    }

  // todo delete callback when v1 migration complete - common/dynamo
  @inline
  def wrapAsyncMethod[Request <: AmazonWebServiceRequest, Result](
    f: (Request, AsyncHandler[Request, Result]) => JFuture[Result],
    request: Request
  ): Future[Result] = {
    val p = Promise[Result]()
    f(request, promiseToAsyncHandler(p))
    p.future
  }

  @inline
  def wrapCompletableFuture[T](cf: CompletableFuture[T]): Future[T] = {
    val p = Promise[T]()
    cf.whenComplete { (result, exception) =>
      if (exception != null) {
        p.failure(exception)
        ()
      }
      else {
        p.success(result);
        ()
      }
    }
    p.future
  }
}
