package aws

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import java.util.concurrent.{Future => JFuture}
import scala.concurrent.{Future, Promise}

object AWSAsync {
  private def promiseToAsyncHandler[Request <: AmazonWebServiceRequest, Result](p: Promise[Result], request: AmazonWebServiceRequest) =
    new AsyncHandler[Request, Result] {
      override def onError(exception: Exception): Unit = {p.failure(new RuntimeException(s"Dynamo request failed $request", exception)); ()}
      override def onSuccess(request: Request, result: Result): Unit = { p.success(result); () }
    }

  @inline
  def wrapAsyncMethod[Request <: AmazonWebServiceRequest, Result](
    f: (Request, AsyncHandler[Request, Result]) => JFuture[Result],
    request: Request
  ): Future[Result] = {
    val p = Promise[Result]
    f(request, promiseToAsyncHandler(p, request))
    p.future
  }
}
