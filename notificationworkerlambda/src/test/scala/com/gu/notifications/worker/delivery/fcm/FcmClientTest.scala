package com.gu.notifications.worker.delivery.fcm

import _root_.models._
import _root_.models.Importance._
import _root_.models.Link._
import _root_.models.TopicTypes._
import com.google.api.core.ApiFuture
import org.mockito.Mockito.when
import com.google.firebase.{ErrorCode, FirebaseApp}
import com.google.firebase.messaging.{BatchResponse, FirebaseMessaging, FirebaseMessagingException, Message, MulticastMessage, SendResponse}
import com.gu.notifications.worker.delivery.DeliveryException.{BatchCallFailedRequest, FailedRequest, InvalidToken, UnknownReasonFailedRequest}
import com.gu.notifications.worker.delivery.{FcmDeliverySuccess, FcmPayload}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import java.util.UUID
import scala.concurrent.ExecutionContext
import models.FcmConfig
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder
import org.specs2.mock.Mockito

import java.util
import java.util.concurrent.Executor
import scala.jdk.CollectionConverters._

class FcmClientTest extends Specification with Mockito {
  "the FcmClient" should {
    "Parse successful responses as an FcmDeliverySuccess" in new FcmScope {
      // when calling addListener we actually want to execute the runnable, but the current implementation is complicated
      doNothing.when(mockApiFuture).addListener(any[Runnable], any[Executor])
      when(mockApiFuture.get()).thenReturn(notification.id.toString)
      when(mockFirebaseMessaging.sendAsync(any[Message])).thenReturn(mockApiFuture)

      fcmClient.sendNotification(notification.id, token, payload, dryRun)(onCompleteCb)(ec)

      // This is less than ideal, but would require wider changes to the code to return something like
      // a Future[Unit] which would allow us to then Await sendNotification
      Thread.sleep(500)

      deliverySuccess shouldEqual 1
    }

    "Parse errors with an invalid token error code as an InvalidToken" in new FcmScope {
      doNothing.when(mockApiFuture).addListener(any[Runnable], any[Executor])

      // we have to mock because there is no public method for instantiating an error of this type
      val mockFirebaseMessagingException = Mockito.mock[FirebaseMessagingException]
      when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.PERMISSION_DENIED)

      when(mockApiFuture.get()).thenAnswer(_ => throw mockFirebaseMessagingException)
      when(mockFirebaseMessaging.sendAsync(any[Message])).thenReturn(mockApiFuture)

      fcmClient.sendNotification(notification.id, token, payload, dryRun)(onCompleteCb)(ec)
      Thread.sleep(500)

      invalidTokens shouldEqual 1
    }

    "Parse errors with NOT_FOUND error code and 'Requested entity was not found.' error message as an InvalidToken" in new FcmScope {
      doNothing.when(mockApiFuture).addListener(any[Runnable], any[Executor])

      val mockFirebaseMessagingException = Mockito.mock[FirebaseMessagingException]
      when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.NOT_FOUND)
      when(mockFirebaseMessagingException.getMessage).thenReturn("Requested entity was not found.")

      when(mockApiFuture.get()).thenAnswer(_ => throw mockFirebaseMessagingException)
      when(mockFirebaseMessaging.sendAsync(any[Message])).thenReturn(mockApiFuture)

      fcmClient.sendNotification(notification.id, token, payload, dryRun)(onCompleteCb)(ec)
      Thread.sleep(500)

      invalidTokens shouldEqual 1
    }

    "Parse entirely successful multicast message" in new FcmScope {
      val tokenList: List[String] = List("token1")

      val mockSendResponse = Mockito.mock[SendResponse]
      when(mockSendResponse.isSuccessful()).thenReturn(true)

      val batchResponseSuccess: BatchResponse = new BatchResponse {
        override def getResponses: util.List[SendResponse] = List(mockSendResponse).asJava
        override def getSuccessCount: Int = 1
        override def getFailureCount: Int = 0
      }

      doNothing.when(mockBatchApiFuture).addListener(any[Runnable], any[Executor])
      when(mockBatchApiFuture.get()).thenReturn(batchResponseSuccess)
      when(mockFirebaseMessaging.sendMulticastAsync(any[MulticastMessage])).thenReturn(mockBatchApiFuture)

      fcmClient.sendBatchNotification(notification.id, tokenList, payload, dryRun)(onBatchCompleteCb)(ec)

      Thread.sleep(500)

      deliveryBatchSuccess shouldEqual 1
    }

    "Parse catastrophic errors when sending multicast messages" in new FcmScope {
      val tokenList: List[String] = List("token1")

      doNothing.when(mockBatchApiFuture).addListener(any[Runnable], any[Executor])
      when(mockBatchApiFuture.get()).thenThrow(new Error("catastrophic failure"))
      when(mockFirebaseMessaging.sendMulticastAsync(any[MulticastMessage])).thenReturn(mockBatchApiFuture)

      fcmClient.sendBatchNotification(notification.id, tokenList, payload, dryRun)(onBatchCompleteCb)(ec)

      Thread.sleep(500)

      catastrophicBatchSendResponse shouldEqual 1
    }

    "Parse partially successful multicast messages" in new FcmScope {
      val tokenList: List[String] = List("token1", "token2", "token3")

      val mockFirebaseMessagingException = Mockito.mock[FirebaseMessagingException]
      when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.PERMISSION_DENIED)

      val mockSendResponseFailure = Mockito.mock[SendResponse]
      when(mockSendResponseFailure.isSuccessful()).thenReturn(false)
      when(mockSendResponseFailure.getException()).thenReturn(mockFirebaseMessagingException)

      val mockSendResponse = Mockito.mock[SendResponse]
      when(mockSendResponse.isSuccessful()).thenReturn(true)

      val batchResponseSuccess: BatchResponse = new BatchResponse {
        override def getResponses: util.List[SendResponse] = List(mockSendResponse, mockSendResponseFailure, mockSendResponse, mockSendResponseFailure).asJava
        override def getSuccessCount: Int = 2
        override def getFailureCount: Int = 2
      }

      doNothing.when(mockBatchApiFuture).addListener(any[Runnable], any[Executor])
      when(mockBatchApiFuture.get()).thenReturn(batchResponseSuccess)
      when(mockFirebaseMessaging.sendMulticastAsync(any[MulticastMessage])).thenReturn(mockBatchApiFuture)

      fcmClient.sendBatchNotification(notification.id, tokenList, payload, dryRun)(onBatchCompleteCb)(ec)

      Thread.sleep(500)

      otherBatchResponse shouldEqual 2
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

  def onCompleteCb(message: Either[Throwable, FcmDeliverySuccess]): Unit = {
    message match {
      case Right(_) =>
        deliverySuccess += 1
      case Left(InvalidToken(_, _, _, _)) =>
        invalidTokens += 1
      case Left(FailedRequest(_, _, _, _)) =>
        failedRequest += 1
      case Left(UnknownReasonFailedRequest(_, _)) =>
        unknownReasonFailedRequest += 1
      case _ => otherResponse += 1
    }
  }

  def onBatchCompleteCb(message: Either[Throwable, FcmDeliverySuccess]): Unit = {
    message match {
      case Right(_) =>
        deliveryBatchSuccess += 1
      case Left(BatchCallFailedRequest(_, _)) =>
        catastrophicBatchSendResponse += 1
      case _ => otherBatchResponse += 1
    }
  }
  val ec = ExecutionContext.global
  val dryRun = false

  val app: FirebaseApp = Mockito.mock[FirebaseApp]
  val mockFirebaseMessaging = Mockito.mock[FirebaseMessaging]
  val mockApiFuture = Mockito.mock[ApiFuture[String]]
  val mockBatchApiFuture = Mockito.mock[ApiFuture[BatchResponse]]

  val config: FcmConfig = FcmConfig("serviceAccountKey")
  val fcmClient = new FcmClient(mockFirebaseMessaging, app, config)
}
