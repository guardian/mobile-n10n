package com.gu.notifications.worker.delivery.fcm.oktransport

import java.io.InputStream

import com.google.api.client.http.LowLevelHttpResponse

class OkGoogleHttpResponse(
  maybeContent: Option[InputStream],
  contentEncoding: Option[String],
  maybeContentLength: Option[Long],
  maybeContentType: Option[String],
  protocol: String,
  statusCode: Int,
  maybeStatusReason: Option[String],
  headers: List[(String, String)]
) extends LowLevelHttpResponse {
  override def getContent: InputStream = maybeContent.orNull

  override def getContentEncoding: String = contentEncoding.orNull

  override def getContentLength: Long = maybeContentLength.getOrElse(0L)

  override def getContentType: String = maybeContentType.orNull

  override def getStatusLine: String = maybeStatusReason.map(statusReason => s"$protocol $statusCode $statusReason").getOrElse(s"$protocol $statusCode")

  override def getStatusCode: Int = statusCode

  override def getReasonPhrase: String = maybeStatusReason.orNull

  override def getHeaderCount: Int = headers.size

  override def getHeaderName(index: Int): String = headers(index)._1

  override def getHeaderValue(index: Int): String = headers(index)._2
}

