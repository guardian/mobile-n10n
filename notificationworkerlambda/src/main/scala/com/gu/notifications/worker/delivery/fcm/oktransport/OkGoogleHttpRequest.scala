package com.gu.notifications.worker.delivery.fcm.oktransport

import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue

import com.google.api.client.http.{LowLevelHttpRequest, LowLevelHttpResponse}
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody, Response}
import okio.BufferedSink

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer


class OkGoogleHttpRequest(okHttpClient: OkHttpClient, url: String, method: String) extends LowLevelHttpRequest {
  //No idea whether FCM now or in the future will call addHeaders concurrently
  private val headers = new ConcurrentLinkedQueue[(String, String)]()
  override def addHeader(name: String, value: String): Unit = headers.offer((name, value))
  override def execute(): LowLevelHttpResponse = {
    val response: Response = executeRequest()
    val (maybeContentType: Option[String], maybeBytes: Option[Long], maybeContent: Option[ByteArrayInputStream]) = readBody(response)
    val headerList: scala.List[(String, String)] = readHeaders(response)
    val maybeContentEncoding = Option(response.header("content-encoding"))
    val httpProtocolInUpperCase = response.protocol().toString.toUpperCase
    val statusCode = response.code
    val statusReason = Option(response.message()).map(_.trim).filterNot(_.isEmpty)
    new OkGoogleHttpResponse(
      maybeContent,
      maybeContentEncoding,
      maybeBytes,
      maybeContentType,
      httpProtocolInUpperCase,
      statusCode,
      statusReason,
      headerList
    )
  }

  private def readHeaders(response: Response): List[(String, String)] = {
    val buffer = ArrayBuffer[(String, String)]()
    val okHeaders = response.headers()
    val count = okHeaders.size()
    for (index <- 0 until count) {
      buffer += ((okHeaders.name(index), okHeaders.value(index)))
    }
    buffer.toList
  }

  private def readBody(response: Response): (Option[String], Option[Long], Option[ByteArrayInputStream]) = {
    val maybeBody = Option(response.body())
    val maybeContentType = maybeBody.flatMap(body => Option(body.contentType())).map(_.toString)
    val maybeBytes = maybeBody.flatMap(body => Option(body.bytes()))
    val maybeContent = maybeBytes.map(new ByteArrayInputStream(_))
    maybeBody.foreach(_.close())
    (maybeContentType, maybeBytes.map(_.length.toLong), maybeContent)
  }

  private def executeRequest(): Response = {
    val requestBuilder: Request.Builder = new Request.Builder().url(url)
    addHeadersToRequest(requestBuilder)
    okHttpClient.newCall(requestBuilder
      .method(method, Option(getStreamingContent).map(content => {
        new RequestBody {
          override def contentType(): MediaType = MediaType.parse(getContentType)
          override def writeTo(sink: BufferedSink): Unit = {
            content.writeTo(sink.outputStream())
          }
        }
      }).orNull)
      .build()).execute()
  }

  private def addHeadersToRequest(requestBuilder: Request.Builder) = {
    Option(this.getContentLength).filter(_ >= 0).foreach(contentLength => addHeader("Content-Length", contentLength.toString))
    Option(this.getContentEncoding).foreach(contentEncoding => addHeader("Content-Encoding", contentEncoding))
    Option(this.getContentType).foreach(contentType => addHeader("Content-Type", contentType))
    @tailrec
    def pollThroughHeaders(): Unit =
      Option(headers.poll()) match {
        case Some((name, value)) => {
          requestBuilder.addHeader(name, value)
          pollThroughHeaders()
        }
        case _ => ()
      }
    pollThroughHeaders()
  }
}

