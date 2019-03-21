package fakebreakingnews

import java.io.IOException
import java.nio.charset.StandardCharsets

import com.gu.mobile.notifications.client.{ContentType, HttpError, HttpOk, HttpProvider, HttpResponse}
import fakebreakingnews.RequestToPromise.requestToPromise
import okhttp3.{Call, Callback, MediaType, OkHttpClient, Request, RequestBody, Response}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Try}

object RequestToPromise {

  def requestToPromise[T](okHttpClient: OkHttpClient, request: Request, transform: (Int, Option[Array[Byte]]) => T) = {
    val promise = Promise[T]
    val url = request.url()
    val method = request.method()
    okHttpClient.newCall(request).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(new Exception(s"$method $url", e))

      override def onResponse(call: Call, response: Response): Unit = promise.complete(
        Try {
          val maybeBytes = Option(response.body).map(_.bytes())
          transform(response.code, maybeBytes)
        }.recoverWith { case e => Failure(new Exception(s"$method $url", e)) }
      )
    })
    promise.future
  }
}

class OkHttpProvider(okhttp: OkHttpClient) extends HttpProvider {
  def codeAndBodyToHttpResponse(code: Int, maybeBodyArray: Option[Array[Byte]]): HttpResponse = {
    val body = maybeBodyArray.map(new String(_, StandardCharsets.UTF_8)).getOrElse("")
    if (code >= 200 && code < 300) {
      HttpOk(code, body)
    } else {
      HttpError(code, body)
    }
  }
  override def post(url: String, apiKey: String, contentType: ContentType, body: Array[Byte]): Future[HttpResponse] = {
    val request = new Request.Builder()
      .url(url)
      .addHeader("Authorization", s"Bearer $apiKey")
      .post(RequestBody.create(MediaType.get(s"${contentType.mediaType}; charset=${contentType.charset}"), body))
      .build()
    requestToPromise(okhttp, request, codeAndBodyToHttpResponse)
  }

  override def get(url: String): Future[HttpResponse] = {
    val request = new Request.Builder()
      .url(url)
      .get()
      .build()
    requestToPromise(okhttp, request, codeAndBodyToHttpResponse)
  }
}
