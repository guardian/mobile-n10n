package com.gu.mobile.notifications.football.lib

import okhttp3._

import scala.concurrent.{ExecutionContext, Future}

class NotificationHttpProvider(implicit ec: ExecutionContext) extends HttpProvider {

  val httpClient = new OkHttpClient

  override def post(uri: String, apiKey: String, contentType: ContentType, body: Array[Byte]): Future[HttpResponse] = {
    val authHeader = s"Bearer $apiKey"
    val mediaType = MediaType.parse(s"${contentType.mediaType}; charset=${contentType.charset}")
    val requestBody = RequestBody.create(mediaType, body)
    val httpRequest = new Request.Builder()
      .url(uri)
      .header("Authorization", authHeader)
      .post(requestBody)
      .build()
    val httpResponse = httpClient.newCall(httpRequest).execute()
    Future.successful(extract(httpResponse))
  }

  override def get(uri: String): Future[HttpResponse] = {
    val httpRequest = new Request.Builder().url(uri).build
    val httpResponse = httpClient.newCall(httpRequest).execute
    Future.successful(extract(httpResponse))
  }

  private def extract(response: Response) = {
    if (response.code >= 200 && response.code < 300)
      HttpOk(response.code, response.body.string)
    else
      HttpError(response.code, response.body.string)
  }
}