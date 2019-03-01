package com.google.api.client.http.javanet

import java.io.{ByteArrayInputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import com.google.api.client.http.LowLevelHttpResponse
import com.gu.notifications.worker.delivery.fcm.oktransport.OkGoogleHttpTransport
import org.apache.commons.io.IOUtils
import org.specs2.matcher.Matchers
import org.specs2.mutable.{BeforeAfter, Specification}
import play.api.http.ContentTypes

import scala.collection.mutable.ArrayBuffer

object HttpResponseMatcher {
  def apply(lowLevelHttpResponse: LowLevelHttpResponse): HttpResponseMatcher = {
    val headerBuilder = ArrayBuffer[(String, String)]()
    val size = lowLevelHttpResponse.getHeaderCount
    for (i <- 0 until size) {
      headerBuilder += ((lowLevelHttpResponse.getHeaderName(i), lowLevelHttpResponse.getHeaderValue(i)))
    }
    val filterOut = Set("X-Cache-Hits", "X-Served-By", "Age", "Date", "Content-Length", "X-Cache", "Expires", "X-Timer", "Set-Cookie", "ETag")
    HttpResponseMatcher(
      IOUtils.toByteArray(lowLevelHttpResponse.getContent).toList,
      lowLevelHttpResponse.getContentEncoding,
      lowLevelHttpResponse.getContentLength,
      lowLevelHttpResponse.getContentType,
      lowLevelHttpResponse.getStatusLine,
      lowLevelHttpResponse.getStatusCode,
      lowLevelHttpResponse.getReasonPhrase
      , headerBuilder.toSet.toList.filterNot(kv => filterOut.contains(kv._1)).sortBy(_._1)
    )
  }

}

case class HttpResponseMatcher(
  content: List[Byte],
  getContentEncoding: String,
  getContentLength: Long,
  getContentType: String,
  getStatusLine: String,
  getStatusCode: Int,
  getReasonPhrase: String,
  headers: List[(String, String)]
) extends LowLevelHttpResponse {


  override def getHeaderCount: Int = headers.size

  override def getHeaderName(index: Int): String = headers(index)._1

  override def getHeaderValue(index: Int): String = headers(index)._2

  override def getContent: InputStream = new ByteArrayInputStream(content.toArray)
}

class OkHttpTransportTest extends Specification with Matchers {

  trait Client extends BeforeAfter {
    var okHttpTransport: OkGoogleHttpTransport = null
    var netHttpTransport: NetHttpTransport = null

    def before = {
      okHttpTransport = new OkGoogleHttpTransport()
      netHttpTransport = new NetHttpTransport()
    }

    def after = {
      okHttpTransport.shutdown()
      netHttpTransport.shutdown()
    }

    private val getMethod = "GET"
    private val postMethod = "POST"

    def post(url: String, json: String): (LowLevelHttpResponse, LowLevelHttpResponse) = {
      (okPost(url, json), netPost(url, json))
    }

    def okPost(url: String, json: String) = {
      val bytes = json.getBytes(StandardCharsets.UTF_8)
      val okRequest = okHttpTransport.buildRequest(postMethod, url)
      okRequest.setStreamingContent((out: OutputStream) => {
        IOUtils.write(bytes, out)
        out.flush()
      })
      okRequest.setContentType(ContentTypes.JSON)
      okRequest.setContentLength(bytes.length)
      okRequest.execute()
    }

    def netPost(url: String, json: String) = {
      val bytes = json.getBytes(StandardCharsets.UTF_8)
      val netRequest = netHttpTransport.buildRequest(postMethod, url)
      netRequest.setStreamingContent((out: OutputStream) => {
        IOUtils.write(bytes, out)
        out.flush()
      })
      netRequest.setContentType(ContentTypes.JSON)
      netRequest.setContentLength(bytes.length)
      netRequest.execute()
    }

    def fetch(url: String): (LowLevelHttpResponse, LowLevelHttpResponse) = {
      (okFetch(url), netFetch(url))
    }

    def netFetch(url: String) = {
      netHttpTransport.buildRequest(getMethod, url).execute()
    }

    def okFetch(url: String) = {
      okHttpTransport.buildRequest(getMethod, url).execute()
    }
  }

  "OkHttpTransport and NetHttpTransport" should {
    "match on redirect" in new Client {
      val (okResponse: LowLevelHttpResponse, netResponse: LowLevelHttpResponse) = fetch("https://www.theguardian.com")

      HttpResponseMatcher(okResponse) must beEqualTo(HttpResponseMatcher(netResponse))
    }.pendingUntilFixed
    "match on 200" in new Client {
      val (okResponse: LowLevelHttpResponse, netResponse: LowLevelHttpResponse) = fetch("https://www.theguardian.com/_not_found")
      HttpResponseMatcher(okResponse) must beEqualTo(HttpResponseMatcher(netResponse))
    }.pendingUntilFixed
    "post" in new Client {
      val (okResponse: LowLevelHttpResponse, netResponse: LowLevelHttpResponse) = post("https://www.theguardian.com", "{}")
      HttpResponseMatcher(okResponse) must beEqualTo(HttpResponseMatcher(netResponse))
    }.pendingUntilFixed
  }


}
