package com.gu.notifications.worker.delivery.fcm

import _root_.models._
import _root_.models.Importance._
import _root_.models.Link._
import _root_.models.TopicTypes._
import com.google.api.core.ApiFuture
import com.google.firebase.{ErrorCode, FirebaseApp}
import com.google.firebase.messaging.{BatchResponse, FirebaseMessaging, FirebaseMessagingException, SendResponse}
import com.gu.notifications.worker.delivery.DeliveryException.{BatchCallFailedRequest, FailedRequest, InvalidToken, UnknownReasonFailedRequest}
import com.gu.notifications.worker.delivery.{BatchDeliverySuccess, DeliveryException, FcmBatchDeliverySuccess, FcmDeliverySuccess, FcmPayload}
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder
import models.FcmConfig
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.mockito.Mockito.when

import java.time.Instant
import java.util
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}
import com.google.auth.oauth2.GoogleCredentials
import com.google.api.client.json.JsonFactory
import com.google.firebase.messaging.MessagingErrorCode
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmErrorPayload

class FcmClientTest extends Specification with Mockito {
  "the FcmClient" should {
    "Parse successful responses as an FcmDeliverySuccess" in new FcmScope {
      val now = Instant.now()
      val response = fcmClient.parseSendResponse(notification.id, token, Success(notification.id.toString), now)

      response shouldEqual Right(FcmDeliverySuccess(token, notification.id.toString, now))
    }

    "Parse errors with an invalid token error code as an InvalidToken" in new FcmScope {
      val fcmException = InvalidTokenException(FcmErrorPayload(500, "Invalid", MessagingErrorCode.INVALID_ARGUMENT.name()))
      val now = Instant.now()
      val response = fcmClient.parseSendResponse(notification.id, token, Failure(fcmException), now)

      response shouldEqual Left(InvalidToken(notification.id, token, fcmException.getMessage()))
    }

    "Parse errors with NOT_FOUND error code and 'Requested entity was not found.' error message as an InvalidToken" in new FcmScope {
      val fcmException = InvalidTokenException(FcmErrorPayload(500, "Requested entity was not found.", MessagingErrorCode.UNREGISTERED.name()))

      val now = Instant.now()
      val response = fcmClient.parseSendResponse(notification.id, token, Failure(fcmException), now)

      response shouldEqual Left(InvalidToken(notification.id, token, fcmException.getMessage()))
    }

    "Parse catastrophic errors when sending multicast messages" in new FcmScope {
      val tokenList: List[String] = List("token1")

      val now = Instant.now()
      fcmClient.parseBatchSendResponse(notification.id, tokenList, Failure(new Error("catastrophic failure")), now)(onCompleteCb)

      catastrophicBatchSendResponse shouldEqual 1
    }

    "Parse partially successful multicast messages" in new FcmScope {
      val tokenList: List[String] = List("token1", "token2", "token3", "token4")

      val mockFirebaseMessagingException = mock[FirebaseMessagingException]
        when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.INTERNAL)

      val mockSendResponseFailure = mock[SendResponse]
      when(mockSendResponseFailure.isSuccessful()).thenReturn(false)
      when(mockSendResponseFailure.getException()).thenReturn(mockFirebaseMessagingException)

      val mockSendResponse = mock[SendResponse]
      when(mockSendResponse.isSuccessful()).thenReturn(true)

      val batchResponseSuccess: BatchResponse = new BatchResponse {
        override def getResponses: util.List[SendResponse] = List(mockSendResponse, mockSendResponseFailure, mockSendResponse, mockSendResponseFailure).asJava
        override def getSuccessCount: Int = 2
        override def getFailureCount: Int = 2
      }

      val now = Instant.now()
      fcmClient.parseBatchSendResponse(notification.id, tokenList, Success(batchResponseSuccess), now)(onCompleteCb)

      failedRequest shouldEqual 2
    }

    "Parse partially successful multicast messages with invalid tokens" in new FcmScope {
      val tokenList: List[String] = List("token1", "token2", "token3", "token4")

      val mockFirebaseMessagingException = mock[FirebaseMessagingException]
      when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.PERMISSION_DENIED)

      val mockSendResponseFailure = mock[SendResponse]
      when(mockSendResponseFailure.isSuccessful()).thenReturn(false)
      when(mockSendResponseFailure.getException()).thenReturn(mockFirebaseMessagingException)

      val mockSendResponse = mock[SendResponse]
      when(mockSendResponse.isSuccessful()).thenReturn(true)

      val batchResponseSuccess: BatchResponse = new BatchResponse {
        override def getResponses: util.List[SendResponse] = List(mockSendResponse, mockSendResponseFailure, mockSendResponse, mockSendResponseFailure).asJava
        override def getSuccessCount: Int = 2
        override def getFailureCount: Int = 2
      }

      val now = Instant.now()
      fcmClient.parseBatchSendResponse(notification.id, tokenList, Success(batchResponseSuccess), now)(onCompleteCb)

      invalidTokens shouldEqual 2
      batchInvalidTokens.contains("token2") shouldEqual true
      batchInvalidTokens.contains("token4") shouldEqual true
    }
  }
}
trait FcmScope extends Scope {
  val notification = BreakingNewsNotification(
    id = UUID.fromString("068b3d2b-dc9d-482b-a1c9-bd0f5dd8ebd7"),
    `type` = NotificationType.BreakingNews,
    title = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
    message = Some("French president Francois Hollande says killers of Normandy priest claimed to be from Islamic State"),
    thumbnailUrl = None,
    sender = "matt.wells@guardian.co.uk",
    link = Internal("world/2016/jul/26/men-hostages-french-church-police-normandy-saint-etienne-du-rouvray", Some("https://gu.com/p/4p7xt"), GITContent, None),
    imageUrl = None,
    importance = Major,
    topic = List(Topic(Breaking, "uk"), Topic(Breaking, "us"), Topic(Breaking, "au"), Topic(Breaking, "international")),
    dryRun = None
  )
  val token = "token"
  val payload: FcmPayload = FcmPayloadBuilder(notification, false).get

  var invalidTokens = 0
  var deliverySuccess = 0
  var failedRequest = 0
  var unknownReasonFailedRequest = 0
  var otherResponse = 0
  var deliveryBatchSuccess = 0
  var otherBatchResponse = 0
  var catastrophicBatchSendResponse = 0
  val batchInvalidTokens = ListBuffer.empty[String]

  def onCompleteCb(message: Either[DeliveryException, BatchDeliverySuccess]): Unit = {
    message match {
      case Right(FcmBatchDeliverySuccess(responses, _)) =>
            responses.foreach {
              case Right(_) =>
                deliverySuccess += 1
              case Left(InvalidToken(_, token, _, _)) =>
                invalidTokens += 1
                batchInvalidTokens += token
              case Left(FailedRequest(_, _, _, _)) =>
                failedRequest += 1
              case Left(UnknownReasonFailedRequest(_, _)) =>
                unknownReasonFailedRequest += 1
              case _ => otherResponse += 1
            }
      case Left(BatchCallFailedRequest(_, _)) =>
        catastrophicBatchSendResponse += 1
      case _ => otherResponse += 1
    }
  }

  val dryRun = false
  val app: FirebaseApp = Mockito.mock[FirebaseApp]
  val mockFirebaseMessaging = Mockito.mock[FirebaseMessaging]
  val mockApiFuture = Mockito.mock[ApiFuture[String]]

  val config: FcmConfig = FcmConfig("serviceAccountKey")
  val mockCredential = Mockito.mock[GoogleCredentials]
  val mockJsonFactory = Mockito.mock[JsonFactory]
  val fcmClient = new FcmClient(mockFirebaseMessaging, app, config, "TEST-PROJECT-ID", mockCredential, mockJsonFactory)
}
