package com.gu.notifications.worker.delivery.fcm.oktransport

import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue

import com.google.api.client.http.{LowLevelHttpRequest, LowLevelHttpResponse}
import okhttp3.{Headers, MediaType, OkHttpClient, Request, RequestBody, Response, ResponseBody}
import okio.BufferedSink

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer


class OkGoogleHttpRequest(okHttpClient: OkHttpClient, url: String, method: String) extends LowLevelHttpRequest {
  //No idea whether FCM now or in the future will call addHeaders concurrently.
  private val headers = new ConcurrentLinkedQueue[(String, String)]()

  override def addHeader(name: String, value: String): Unit = headers.offer((name, value))

  override def execute(): LowLevelHttpResponse = {
    val response: Response = executeRequest()

    val (maybeContentType: Option[String], maybeBodyBytes: Option[Array[Byte]]) = callOnceWithResponseBody(response)(responseBody =>
      (Option(responseBody.contentType()).map(_.toString), Option(responseBody.bytes()))
    ).getOrElse(None, None)

    val maybeContent: Option[ByteArrayInputStream] = maybeBodyBytes.map(new ByteArrayInputStream(_))
    val maybeContentLength: Option[Long] = maybeBodyBytes.map(_.length.toLong)

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

  private def callOnceWithResponseBody[A](response: Response)(f: ResponseBody => A): Option[A] = {
    val body = Option(response.body())
    try {
      body.map(f)
    }
    finally {
      body.foreach(_.close())
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

