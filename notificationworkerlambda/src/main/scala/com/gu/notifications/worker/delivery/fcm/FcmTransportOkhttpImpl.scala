package com.gu.notifications.worker.delivery.fcm

import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, Promise}
import scala.util.{Try, Success, Failure}
import scala.util.Random
import okhttp3.{Call, Callback, ConnectionPool, Dns, Headers, MediaType, OkHttpClient, Request, RequestBody, Response, ResponseBody}
import play.api.libs.json.{Format, Json, JsError, JsValue, JsSuccess}
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.client.json.{JsonFactory, JsonGenerator}
import com.google.firebase.messaging._
import com.gu.notifications.worker.delivery.FcmPayload
import com.gu.notifications.worker.delivery.fcm.models.payload.{FcmResponse, FcmError, FcmErrorPayload}

case class InvalidResponseException(responseBody: String) extends Exception("Invalid success response") {
  override def getMessage(): String = {
    s"${super.getMessage()}.  Response body: [${responseBody.take(200)}]"
  }
}

case class QuotaExceededException(details: FcmErrorPayload) extends Exception("Request quota exceeded") {
  override def getMessage(): String = {
    s"${super.getMessage()}.  Details: ${details.toString()}]"
  }
}

case class InvalidTokenException(details: FcmErrorPayload) extends Exception("Invalid device token") {
  override def getMessage(): String = {
    s"${super.getMessage()}.  Details: ${details.toString()}]"
  }
}

case class FcmServerException(details: FcmErrorPayload) extends Exception("FCM server error") {
  override def getMessage(): String = {
    s"${super.getMessage()}.  Details: ${details.toString()}]"
  }
}

case class UnknownException(details: FcmErrorPayload) extends Exception("Unexpected exception") {
  override def getMessage(): String = {
    s"${super.getMessage()}.  Details: ${details.toString()}]"
  }
}

case class FcmServerTransportException(ex: Throwable) extends Exception("Failed to send HTTP request", ex) {
  override def getMessage(): String = {
    s"${super.getMessage()}.  Reason: ${ex.getMessage()}]"
  }
}

class FcmTransportOkhttpImpl(credential: GoogleCredentials, url: String, jsonFactory: JsonFactory) {
  
  private val okHttpClient: OkHttpClient = new OkHttpClient.Builder()
    .dns((hostname: String) => Random.shuffle(Dns.SYSTEM.lookup(hostname).asScala).asJava)
    .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
    .followRedirects(false)
    .build()
  okHttpClient.dispatcher().setMaxRequests(500)

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = MediaType.parse("application/json; charset=UTF-8")

  private val authFcmScope = "https://www.googleapis.com/auth/firebase.messaging"

  private val scopedCredential = credential.createScoped(authFcmScope)

  def shutdown: Unit = {
    okHttpClient.dispatcher().executorService().shutdown()
    okHttpClient.connectionPool().evictAll()
    Option(okHttpClient.cache()).foreach(_.close())
  }
  
  private def getAccessToken(): String = {
    scopedCredential.refreshIfExpired()
    scopedCredential.getAccessToken().getTokenValue()
  }

  private def createBody(message: Message, dryRun: Boolean): Array[Byte] = {
    val sink = new ByteArrayOutputStream()
    val generator: JsonGenerator = jsonFactory.createJsonGenerator(sink, charSet);
    val data = if (dryRun) 
                  Map("message" -> message,
                      "validate_only" -> true)
               else
                  Map("message" -> message)
    generator.serialize(data.asJava)
    generator.flush()
    sink.toByteArray()
  }

  private def parseBody(responseBody: ResponseBody): Try[FcmResponse] = {
    val json: JsValue = Json.parse(responseBody.string())
    json.validate[FcmResponse] match {
      case JsSuccess(message, _) => Success(message)
      case JsError(errors)       => Failure(InvalidResponseException(responseBody.string()))
    }
  }

  private def parseError(responseBody: ResponseBody): Try[FcmError] = {
    val json: JsValue = Json.parse(responseBody.string())
    json.validate[FcmError] match {
      case JsSuccess(message, _) => Success(message)
      case JsError(errors)       => Failure(InvalidResponseException(responseBody.string()))
    }
  }

  val invalidTokenErrorCodes = Set(
    MessagingErrorCode.INVALID_ARGUMENT,
    MessagingErrorCode.UNREGISTERED,
    MessagingErrorCode.SENDER_ID_MISMATCH).map(_.name())

  val internalServerErrorCodes = Set(
    MessagingErrorCode.UNAVAILABLE,
    MessagingErrorCode.INTERNAL,
    MessagingErrorCode.THIRD_PARTY_AUTH_ERROR).map(_.name())

  val quotaExceededErrorCodes = Set(
    MessagingErrorCode.QUOTA_EXCEEDED).map(_.name())

  private def handleResponse(response: Response): Try[FcmResponse] = {
    if (response.code == 200)
      parseBody(response.body())
    else
      parseError(response.body()).flatMap(fcmError => fcmError.error.status match {
        case code if invalidTokenErrorCodes.contains(code) => Failure(InvalidTokenException(fcmError.error))
        case code if internalServerErrorCodes.contains(code) => Failure(FcmServerException(fcmError.error))
        case code if quotaExceededErrorCodes.contains(code) => Failure(QuotaExceededException(fcmError.error))
        case _ => Failure(UnknownException(fcmError.error))
      })
  }

  def sendAsync(token: String, payload: FcmPayload, dryRun: Boolean): Future[String] = {
    val message = Message
          .builder
          .setToken(token)
          .setAndroidConfig(payload.androidConfig)
          .build
    val body = createBody(message, dryRun)
    val request: Request = new Request.Builder()
      .url(url)
      .addHeader("Authorization", "Bearer " + getAccessToken())
      .post(RequestBody.create(body, mediaType))
      .build();
    val p = Promise[String]()
    okHttpClient.newCall(request).enqueue(new Callback() {
      def onFailure(call: Call, ex: IOException): Unit = {
        ex.printStackTrace()
        p.failure(FcmServerTransportException(ex))
      }
      def onResponse(call: Call, response: Response): Unit = {
        handleResponse(response).fold(
          ex => p.failure(ex),
          response => p.success(response.name)
        )
      }
    })
    p.future
  }
}
