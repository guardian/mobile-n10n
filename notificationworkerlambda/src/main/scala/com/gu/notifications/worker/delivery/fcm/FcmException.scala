package com.gu.notifications.worker.delivery.fcm

import com.gu.notifications.worker.delivery.fcm.models.payload.FcmErrorPayload

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