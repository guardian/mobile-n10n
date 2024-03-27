package com.gu.notifications.worker.delivery.fcm

import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, Promise}
import scala.util.{Try, Failure}
import scala.util.Random
import okhttp3.{Call, Callback, ConnectionPool, Dns, Headers, MediaType, OkHttpClient, Request, RequestBody, Response, ResponseBody}
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.client.json.{Json, JsonFactory, JsonParser, JsonGenerator}
import com.google.firebase.messaging._
import com.google.api.client.util.Key
import com.gu.notifications.worker.delivery.FcmPayload

case class FcmResponse(@Key("name") messageId: String)

case class FcmErrorPayload(@Key("code") code: Int, @Key("message") message: String, @Key("status") status: String)

case class FcmError(@Key("error") payload: FcmErrorPayload)

case class InvalidResponseException(message: String, responseBody: String, err: Throwable) extends Exception(message, err)

case class QuotaExceededException(message: String, details: FcmErrorPayload) extends Exception(message)

case class InvalidTokenException(message: String, details: FcmErrorPayload) extends Exception(message)

case class FcmServerException(message: String, details: FcmErrorPayload) extends Exception(message)

case class UnknownException(message: String, details: FcmErrorPayload) extends Exception(message)

case class FcmServerTransportException(message: String, ex: Throwable) extends Exception(message, ex)

class FcmTransportMultiplexedHttp2Impl(credential: GoogleCredentials, url: String, jsonFactory: JsonFactory) {
  
  private val okHttpClient: OkHttpClient = new OkHttpClient.Builder()
    .dns((hostname: String) => Random.shuffle(Dns.SYSTEM.lookup(hostname).asScala).asJava)
    .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
    .followRedirects(false)
    .build()
  okHttpClient.dispatcher().setMaxRequests(500)

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = MediaType.parse(Json.MEDIA_TYPE)

  def shutdown: Unit = {
    okHttpClient.dispatcher().executorService().shutdown()
    okHttpClient.connectionPool().evictAll()
    Option(okHttpClient.cache()).foreach(_.close())
  }
  
  private def getAccessToken(): String = {
    credential.refreshIfExpired()
    credential.getAccessToken().getTokenValue()
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

  private def parseBody(responseBody: ResponseBody): Try[FcmResponse] = Try {
    val parser: JsonParser = jsonFactory.createJsonParser(responseBody.byteStream())
    parser.parseAndClose(FcmResponse.getClass()).asInstanceOf[FcmResponse]
  }.recoverWith {
    case e => Failure(InvalidResponseException("Invalid response for success", responseBody.string(), e))
  }

  private def parseError(responseBody: ResponseBody): Try[FcmError] = Try {
    val parser: JsonParser = jsonFactory.createJsonParser(responseBody.byteStream())
    parser.parseAndClose(FcmError.getClass()).asInstanceOf[FcmError]
  }.recoverWith {
    case e => Failure(InvalidResponseException("Invalid response for failed", responseBody.string(), e))
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
      parseError(response.body()).flatMap(fcmError => fcmError.payload.status match {
        case code if invalidTokenErrorCodes.contains(code) => Failure(InvalidTokenException("Invalid device token", fcmError.payload))
        case code if internalServerErrorCodes.contains(code) => Failure(FcmServerException("FCM server error", fcmError.payload))
        case code if quotaExceededErrorCodes.contains(code) => Failure(QuotaExceededException("Request quota exceeded", fcmError.payload))
        case _ => Failure(UnknownException("Unexpected exception", fcmError.payload))
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
      def onFailure(call: Call, ex: IOException): Unit = (
        p.failure(FcmServerTransportException(s"Failed to send HTTP request - ${ex.getMessage}", ex))
      )
      def onResponse(call: Call, response: Response): Unit = (
        handleResponse(response).fold(
          ex => p.failure(ex),
          response => p.success(response.messageId)
        )
      )
    })
    p.future
  }
}
