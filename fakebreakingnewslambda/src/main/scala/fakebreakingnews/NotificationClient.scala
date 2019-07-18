package fakebreakingnews

import java.io.IOException
import java.nio.charset.StandardCharsets

import com.gu.mobile.notifications.client.models.NotificationPayload
import fakebreakingnews.RequestToPromise.requestToFuture
import okhttp3.{Call, Callback, MediaType, OkHttpClient, Request, RequestBody, Response}
import play.api.libs.json.Json

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Try}

object RequestToPromise {

  def requestToFuture[T](okHttpClient: OkHttpClient, request: Request, transform: (Int, Option[Array[Byte]]) => T): Future[T] = {
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

class NotificationClient(okhttp: OkHttpClient, host: String, apiKey: String) {
  private val url = s"$host/push/topic"

  def codeAndBodyToHttpResponse(code: Int, maybeBodyArray: Option[Array[Byte]]): String = {
    val body = maybeBodyArray.map(new String(_, StandardCharsets.UTF_8)).getOrElse("")
    if (code >= 200 && code < 300) {
      s"OK: HTTP $code $body"
    } else {
      s"ERROR: HTTP $code $body"
    }
  }

  def send(notification: NotificationPayload): Future[String] = {
    val body = Json.stringify(NotificationPayload.jf.writes(notification))
    val request = new Request.Builder()
      .url(url)
      .addHeader("Authorization", s"Bearer $apiKey")
      .post(RequestBody.create(MediaType.get(s"application/json; charset=UTF-8"), body))
      .build()
    requestToFuture(okhttp, request, codeAndBodyToHttpResponse)
  }

}
