package com.gu.notifications.worker.delivery.fcm

import _root_.models._
import _root_.models.Importance._
import _root_.models.Link._
import _root_.models.TopicTypes._
import com.google.api.core.ApiFuture
import com.google.firebase.{ErrorCode, FirebaseApp}
import com.google.firebase.messaging.{BatchResponse, FirebaseMessaging, FirebaseMessagingException, Message, MulticastMessage, SendResponse}
import com.gu.notifications.worker.delivery.DeliveryException.{BatchCallFailedRequest, FailedRequest, InvalidToken, UnknownReasonFailedRequest}
import com.gu.notifications.worker.delivery.{FcmDeliverySuccess, FcmPayload}
import com.gu.notifications.worker.delivery.fcm.models.payload.FcmPayloadBuilder
import models.FcmConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doNothing, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.util
import java.util.concurrent.Executor
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls

class FcmClientTest extends AsyncFlatSpec with MockitoSugar with Eventually with IntegrationPatience {
  "The FcmClient" should "parse successful responses as an FcmDeliverySuccess" in {
    val f = fcmFixure
    // when calling addListener we actually want to execute the runnable, but the current implementation is complicated
    doNothing().when(f.mockApiFuture).addListener(any[Runnable], any[Executor])
    when(f.mockApiFuture.get()).thenReturn(f.notification.id.toString)
    when(f.mockFirebaseMessaging.sendAsync(any[Message])).thenReturn(f.mockApiFuture)

    eventually {
      f.fcmClient.sendNotification(f.notification.id, f.token, f.payload, f.dryRun)(f.onCompleteCb)(f.ec)
      f.deliverySuccess shouldEqual 1
    }
  }

  it should "parse errors with an invalid token error code as an InvalidToken" in {
    val f = fcmFixure

    doNothing().when(f.mockApiFuture).addListener(any[Runnable], any[Executor])

    // we have to mock because there is no public method for instantiating an error of this type
    val mockFirebaseMessagingException = mock[FirebaseMessagingException]
    when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.PERMISSION_DENIED)

    when(f.mockApiFuture.get()).thenAnswer(_ => throw mockFirebaseMessagingException)
    when(f.mockFirebaseMessaging.sendAsync(any[Message])).thenReturn(f.mockApiFuture)

    eventually {
      f.fcmClient.sendNotification(f.notification.id, f.token, f.payload, f.dryRun)(f.onCompleteCb)(f.ec)
      f.invalidTokens shouldEqual 1
    }
  }

  it should "parse errors with NOT_FOUND error code and 'Requested entity was not found.' error message as an InvalidToken" in {
    val f = fcmFixure

    doNothing().when(f.mockApiFuture).addListener(any[Runnable], any[Executor])

    val mockFirebaseMessagingException = mock[FirebaseMessagingException]
    when(mockFirebaseMessagingException.getErrorCode).thenReturn(ErrorCode.NOT_FOUND)
    when(mockFirebaseMessagingException.getMessage).thenReturn("Requested entity was not found.")

    when(f.mockApiFuture.get()).thenAnswer(_ => throw mockFirebaseMessagingException)
    when(f.mockFirebaseMessaging.sendAsync(any[Message])).thenReturn(f.mockApiFuture)

    eventually {
      f.fcmClient.sendNotification(f.notification.id, f.token, f.payload, f.dryRun)(f.onCompleteCb)(f.ec)
      f.invalidTokens shouldEqual 1
    }
  }

  it should "parse entirely successful multicast message" in {
    val f = fcmFixure
    val tokenList: List[String] = List("token1", "token2", "token3")

    val mockSendResponse = mock[SendResponse]
    when(mockSendResponse.isSuccessful()).thenReturn(true)

    val batchResponseSuccess: BatchResponse = new BatchResponse {
      override def getResponses: util.List[SendResponse] = List(mockSendResponse, mockSendResponse, mockSendResponse).asJava

      override def getSuccessCount: Int = 3

      override def getFailureCount: Int = 0
    }

    doNothing().when(f.mockBatchApiFuture).addListener(any[Runnable], any[Executor])
    when(f.mockBatchApiFuture.get()).thenReturn(batchResponseSuccess)
    when(f.mockFirebaseMessaging.sendMulticastAsync(any[MulticastMessage])).thenReturn(f.mockBatchApiFuture)

    eventually {
      f.fcmClient.sendBatchNotification(f.notification.id, tokenList, f.payload, f.dryRun)(f.onBatchCompleteCb)(f.ec)
      f.deliveryBatchSuccess shouldEqual 3
    }
  }

  it should "parse catastrophic errors when sending multicast messages" in {
    val f = fcmFixure
    val tokenList: List[String] = List("token1")

    doNothing().when(f.mockBatchApiFuture).addListener(any[Runnable], any[Executor])
    when(f.mockBatchApiFuture.get()).thenThrow(new Error("catastrophic failure"))
    when(f.mockFirebaseMessaging.sendMulticastAsync(any[MulticastMessage])).thenReturn(f.mockBatchApiFuture)

    eventually {
      f.fcmClient.sendBatchNotification(f.notification.id, tokenList, f.payload, f.dryRun)(f.onBatchCompleteCb)(f.ec)
      f.catastrophicBatchSendResponse shouldEqual 1
    }
  }

  it should "Parse partially successful multicast messages" in {
    val f = fcmFixure
    val tokenList: List[String] = List("token1", "token2", "token3")

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

    doNothing().when(f.mockBatchApiFuture).addListener(any[Runnable], any[Executor])
    when(f.mockBatchApiFuture.get()).thenReturn(batchResponseSuccess)
    when(f.mockFirebaseMessaging.sendMulticastAsync(any[MulticastMessage])).thenReturn(f.mockBatchApiFuture)

    eventually {
      f.fcmClient.sendBatchNotification(f.notification.id, tokenList, f.payload, f.dryRun)(f.onBatchCompleteCb)(f.ec)
      f.otherBatchResponse shouldEqual 2
    }
  }

  def fcmFixure = new {
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

    val app: FirebaseApp = mock[FirebaseApp]
    val mockFirebaseMessaging = mock[FirebaseMessaging]
    val mockApiFuture = mock[ApiFuture[String]]
    val mockBatchApiFuture = mock[ApiFuture[BatchResponse]]

    val config: FcmConfig = FcmConfig("serviceAccountKey")
    val fcmClient = new FcmClient(mockFirebaseMessaging, app, config)
  }
}
