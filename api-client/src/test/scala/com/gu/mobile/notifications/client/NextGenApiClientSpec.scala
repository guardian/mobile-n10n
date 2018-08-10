package com.gu.mobile.notifications.client

import com.gu.mobile.notifications.client.models.TopicTypes._
import com.gu.mobile.notifications.client.models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result
import org.specs2.mock.mockito.ArgumentCapture
import play.api.libs.json.Json
import scala.collection.JavaConverters._

import scala.concurrent.Future


class NextGenApiClientSpec(implicit ee: ExecutionEnv) extends ApiClientSpec[NextGenApiClient] {

  val payload = BreakingNewsPayload(
    title = "myTitle",
    message = "myMessage",
    sender = "test sender",
    imageUrl = None,
    thumbnailUrl = None,
    link = ExternalLink("http://mylink"),
    importance = Importance.Major,
    topic = List(Topic(Breaking, "n1")),
    debug = true
  )

  val expectedPostUrl = s"$host/push/topic?api-key=$apiKey"
  val expectedPostBody = Json.stringify(Json.toJson(payload))

  override def getTestApiClient(httpProvider: HttpProvider) = new NextGenApiClient(
    apiKey = apiKey,
    httpProvider = httpProvider,
    host = host
  )
  def apiTest(test: NextGenApiClient => Unit): Result = {
    val successServerResponse = HttpOk(201, """{"id":"someId"}""")
    apiTest(successServerResponse)(test)
  }

  "NextGenApiClient" should {
    "successfully send payload" in apiTest {
      client => client.send(payload) must beRight.await
    }
    "successfully send multiple HTTP calls for breaking news with more than one tag" in {
      val payLoadMultipleTopics = payload.copy(topic = List(Topic(Breaking, "n1"), Topic(Breaking, "n2")))

      val fakeHttpProvider = mock[HttpProvider]
      fakeHttpProvider.post(anyString, any[ContentType], any[Array[Byte]]) returns Future.successful(HttpOk(201, """{"id":"someId"}"""))

      val testApiClient = getTestApiClient(fakeHttpProvider)
      testApiClient.send(payLoadMultipleTopics)

      val bodyCapture = new ArgumentCapture[Array[Byte]]
      val urlCapture = new ArgumentCapture[String]
      val contentTypeCapture = new ArgumentCapture[ContentType]

      there was two(fakeHttpProvider).post(urlCapture, contentTypeCapture, bodyCapture)
      urlCapture.value mustEqual expectedPostUrl
      contentTypeCapture.value mustEqual ContentType("application/json", "UTF-8")
      val ids = bodyCapture.values
        .asScala.toList
        .map(Json.parse)
        .map(_ \ "id")
      ids(0) mustNotEqual ids(1)
    }
    "return HttpApiError error if http provider returns ApiHttpError" in apiTest(serverResponse = HttpError(500, "")) {
      client => client.send(payload) must beEqualTo(Left(ApiHttpError(status = 500, Some("")))).await
    }
    "return UnexpectedApiResponseError if server returns invalid json" in apiTest(serverResponse = HttpOk(201, "not valid json at all")) {
      client => client.send(payload) must beEqualTo(Left(UnexpectedApiResponseError("not valid json at all"))).await
    }
    "return UnexpectedApiResponseError if server returns wrong json format" in apiTest(serverResponse = HttpOk(201, """{"unexpected":"yes"}""")) {
      client => client.send(payload) must beEqualTo(Left(UnexpectedApiResponseError("""{"unexpected":"yes"}"""))).await
    }
    "return UnexpectedApiResponseError if server returns wrong success status code" in apiTest(serverResponse = HttpOk(200, "success but not code 201!")) {
      client => client.send(payload) must beEqualTo(Left(UnexpectedApiResponseError("Server returned status code 200 and body:success but not code 201!"))).await
    }

    "return HttpProviderError if http provider throws exception" in {
      val throwable = new RuntimeException("something went wrong!!")
      val fakeHttpProvider = mock[HttpProvider]
      fakeHttpProvider.post(anyString, any[ContentType], any[Array[Byte]]) returns Future.failed(throwable)
      val client = getTestApiClient(fakeHttpProvider)
      client.send(payload) must beEqualTo(Left(HttpProviderError(throwable))).await
    }
  }

}
