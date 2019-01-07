package com.gu.notifications.worker.delivery.fcm.oktransport

import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue

import com.google.api.client.http.{LowLevelHttpRequest, LowLevelHttpResponse}
import okhttp3.{Headers, MediaType, OkHttpClient, Request, RequestBody, Response}
import okio.BufferedSink

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer


class OkGoogleHttpRequest(okHttpClient: OkHttpClient, url: String, method: String) extends LowLevelHttpRequest {
  //No idea whether FCM now or in the future will call addHeaders concurrently
  private val headers = new ConcurrentLinkedQueue[(String, String)]()

  override def addHeader(name: String, value: String): Unit = headers.offer((name, value))

  override def execute(): LowLevelHttpResponse = {
    val response: Response = executeRequest()
    val (maybeContentType: Option[String], maybeContentLength: Option[Long], maybeContent: Option[ByteArrayInputStream]) = readBody(response)
    val headerList: scala.List[(String, String)] = readHeaders(response)
    val maybeContentEncoding = Option(response.header("content-encoding"))
    val httpProtocolInUpperCase = response.protocol().toString.toUpperCase
    val statusCode = response.code
    val statusReason = Option(response.message()).map(_.trim).filterNot(_.isEmpty)
    new OkGoogleHttpResponse(
      maybeContent,
      maybeContentEncoding,
      maybeContentLength,
      maybeContentType,
      httpProtocolInUpperCase,
      statusCode,
      statusReason,
      headerList
    )
  }

  private def readHeaders(response: Response): List[(String, String)] = {
    val headerTuples = ArrayBuffer[(String, String)]()
    val okHeaders: Headers = response.headers()
    val headerCount = okHeaders.size()
    for (headerIndex <- 0 until headerCount) {
      headerTuples += ((okHeaders.name(headerIndex), okHeaders.value(headerIndex)))
    }
    headerTuples.toList
  }

  private def readBody(response: Response): (Option[String], Option[Long], Option[ByteArrayInputStream]) = {
    val maybeBody = Option(response.body())
    try {
      val maybeContentType: Option[String] = maybeBody.flatMap(body => Option(body.contentType())).map(_.toString)
      val maybeBytes: Option[Array[Byte]] = maybeBody.flatMap(body => Option(body.bytes()))
      val maybeContent: Option[ByteArrayInputStream] = maybeBytes.map(new ByteArrayInputStream(_))
      (maybeContentType, maybeBytes.map(_.length.toLong), maybeContent)
    }
    finally {
      maybeBody.foreach(_.close())
    }
  }

  private def executeRequest(): Response = okHttpClient.newCall(addHeadersToRequest(new Request.Builder())
    .url(url)
    .method(method, Option(getStreamingContent).map(content => {
      new RequestBody {
        override def contentType(): MediaType = MediaType.parse(getContentType)

        override def writeTo(sink: BufferedSink): Unit = {
          content.writeTo(sink.outputStream())
        }
      }
    }).orNull)
    .build())
    .execute()


  private def addHeadersToRequest(requestBuilder: Request.Builder): Request.Builder = {



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
    requestBuilder
  }
}

