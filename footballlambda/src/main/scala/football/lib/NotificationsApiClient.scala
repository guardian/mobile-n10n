package football

import java.util.UUID

import com.gu.mobile.notifications.client.models.NotificationPayload
import okhttp3._
import play.api.libs.json._

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

case class Response(id: String)
object Response {
  implicit val responseJF: Reads[Response] = Json.reads[Response]
}

class NotificationsApiClient(config: Configuration) extends Logging {
  private val client = new OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .build()

  def send(notification: NotificationPayload): Either[String, UUID] = {
    val url = s"${config.notificationsHost}/push/topic"

    val mediaType = MediaType.parse(s"application/json; charset=utf-8")
    val authHeader = s"Bearer ${config.notificationsApiKey}"
    val body = RequestBody.create(mediaType, Json.toBytes(Json.toJson(notification)))
    val request = new Request.Builder()
      .url(url)
      .header("Authorization", authHeader)
      .post(body)
      .build()

    val result = Try(client.newCall(request).execute()).map { response =>
      val status = response.code
      val responseBody = response.body.string()

      if (status >= 200 && status < 300) {
        parse(responseBody) match {
          case Some(id) => Right(id)
          case None => Left(s"Notification was sent but unable to parse the response. Got status $status and body $responseBody")
        }

      } else {
        Left(s"Unable to send notification, got status $status and body $responseBody")
      }
    }

    result match {
      case Success(value) => value
      case Failure(NonFatal(e)) =>
        logger.error("Unable to send notification", e)
        Left(s"Unable to send notification: ${e.getMessage}")
    }
  }

  def parse(response: String): Option[UUID] = {
    def toUUID(idAttribute: JsValue): Option[UUID] = idAttribute match {
      case JsString(id) => Some(UUID.fromString(id))
      case _ => None
    }

    val parsed = Json.parse(response)
    parsed match {
      case JsObject(attributes) => attributes.get("id").flatMap(toUUID)
      case _ => None
    }
  }

}