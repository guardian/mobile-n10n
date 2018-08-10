package com.gu.mobile.notifications.client

import org.specs2.execute.Result
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.ArgumentCapture
import org.specs2.mutable.Specification
import play.api.libs.json.Json

import scala.concurrent.Future


trait ApiClientSpec[C <: ApiClient] extends Specification with Mockito {
  val host = "http://host.co.uk"
  val apiKey = "apiKey"

  def getTestApiClient(httpProvider: HttpProvider): C
  def expectedPostUrl: String
  def expectedPostBody: String

  def apiTest(serverResponse: HttpResponse)(test: C => Unit): Result = {
    val fakeHttpProvider = mock[HttpProvider]
    fakeHttpProvider.post(anyString, any[ContentType], any[Array[Byte]]) returns Future.successful(serverResponse)

    val testApiClient = getTestApiClient(fakeHttpProvider)
   
    test(testApiClient)

    val bodyCapture = new ArgumentCapture[Array[Byte]]
    val urlCapture = new ArgumentCapture[String]
    val contentTypeCapture = new ArgumentCapture[ContentType]

    there was one(fakeHttpProvider).post(urlCapture, contentTypeCapture, bodyCapture)
    urlCapture.value mustEqual expectedPostUrl
    contentTypeCapture.value mustEqual ContentType("application/json", "UTF-8")
    Json.parse(bodyCapture.value) mustEqual Json.parse(expectedPostBody)
  }
}
