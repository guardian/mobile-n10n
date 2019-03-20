package fakebreakingnews

import java.io.IOException

import com.gu.mobile.notifications.client.{ContentType, HttpError, HttpOk, HttpProvider, HttpResponse}
import okhttp3.{Call, Callback, MediaType, OkHttpClient, Request, RequestBody, Response}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

class OkHttpProvider(okhttp: OkHttpClient) extends HttpProvider {
  override def post(url: String, apiKey: String, contentType: ContentType, body: Array[Byte]): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]
    val call = okhttp.newCall(new Request.Builder()
      .url(url)
      .addHeader("Authorization", s"Bearer $apiKey")
      .post(RequestBody.create(MediaType.get(s"${contentType.mediaType}; charset=${contentType.charset}"), body))
      .build())
    call.enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(new Exception(s"Error posting to $url", e))

      override def onResponse(call: Call, response: Response): Unit = {
        Try {
          val body = Option(response.body).map(_.string()).getOrElse("")
          if (response.code < 400) {
            HttpError(response.code, body)
          }
          else {
            HttpOk(response.code, body)
          }
        }
      } match {
        case Success(httpResponse) => promise.success(httpResponse)
        case Failure(e) => promise.failure(new Exception(s"Error posting to $url", e))
      }
    })
    promise.future
  }

  override def get(url: String): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]
    val call = okhttp.newCall(new Request.Builder()
      .url(url)
      .get()
      .build())
    call.enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(new Exception(s"Error getting $url", e))

      override def onResponse(call: Call, response: Response): Unit = {
        Try {
          val body = Option(response.body).map(_.string()).getOrElse("")
          if (response.code < 400) {
            HttpError(response.code, body)
          }
          else {
            HttpOk(response.code, body)
          }
        }
      } match {
        case Success(httpResponse) => promise.success(httpResponse)
        case Failure(e) => promise.failure(new Exception(s"Error getting $url", e))
      }
    })
    promise.future
  }
}
