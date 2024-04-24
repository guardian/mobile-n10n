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
import play.api.libs.json.{Format, Json, JsError, JsValue, JsSuccess}
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.client.json.{JsonFactory, JsonGenerator}
import com.google.firebase.messaging._
import com.google.firebase.ErrorCode
import com.gu.notifications.worker.delivery.FcmPayload
import com.gu.notifications.worker.delivery.fcm.models.payload.{FcmResponse, FcmError, FcmErrorPayload}
import java.net.http.HttpRequest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import org.slf4j.{Logger, LoggerFactory}

trait FcmTransport {
  def sendAsync(token: String, payload: FcmPayload, dryRun: Boolean): Future[String]
}

class FcmTransportJdkImpl(credential: GoogleCredentials, url: String, jsonFactory: JsonFactory) extends FcmTransport {
  
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val httpClient: HttpClient = HttpClient.newHttpClient()

  logger.info("HttpClient is instantiated")

  private val charSet = StandardCharsets.UTF_8

  private val mediaType = "application/json; charset=UTF-8"

  private val authFcmScope = "https://www.googleapis.com/auth/firebase.messaging"

  private val scopedCredential = credential.createScoped(authFcmScope)
  
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

  private def parseBody(responseBody: String): Try[FcmResponse] = {
    val json: JsValue = Json.parse(responseBody)
    json.validate[FcmResponse] match {
      case JsSuccess(message, _) => Success(message)
      case JsError(errors)       => Failure(InvalidResponseException(responseBody))
    }
  }

  private def parseError(responseBody: String): Try[FcmError] = {
    val json: JsValue = Json.parse(responseBody)
    json.validate[FcmError] match {
      case JsSuccess(message, _) => Success(message)
      case JsError(errors)       => Failure(InvalidResponseException(responseBody))
    }
  }

  val invalidTokenErrorCodes = Set(
    MessagingErrorCode.INVALID_ARGUMENT,
    MessagingErrorCode.UNREGISTERED,
    MessagingErrorCode.SENDER_ID_MISMATCH,
    ErrorCode.PERMISSION_DENIED).map(_.name())

  val internalServerErrorCodes = Set(
    MessagingErrorCode.UNAVAILABLE,
    MessagingErrorCode.INTERNAL,
    MessagingErrorCode.THIRD_PARTY_AUTH_ERROR).map(_.name())

  val quotaExceededErrorCodes = Set(
    MessagingErrorCode.QUOTA_EXCEEDED).map(_.name())

  private def handleResponse(response: HttpResponse[String]): Try[FcmResponse] = {
    if (response.statusCode() == 200)
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

    val request: HttpRequest = HttpRequest.newBuilder(new URI(url))
      .version(HttpClient.Version.HTTP_2)
      .header("Authorization", "Bearer " + getAccessToken())
      .header("Content-Type", mediaType)
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
      .build()
    val p = Promise[String]()
    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) => {
      if (response == null) {
        p.failure(FcmServerTransportException(err))
      } else {
        handleResponse(response).fold(
          ex => p.failure(ex),
          fcmResponse => p.success(fcmResponse.name)
        )
      }
    })
    p.future
  }
}
