package com.gu.mobile.notifications.client

import scala.concurrent.Future

sealed trait HttpResponse

case class HttpOk(status: Int, body: String) extends HttpResponse {
  require(status >= 200 && status < 300)
}

case class HttpError(status: Int, body: String) extends HttpResponse

case class ContentType(mediaType: String, charset: String)

trait HttpProvider {
  def post(url: String, contentType: ContentType, body: Array[Byte]): Future[HttpResponse]

  def get(url: String): Future[HttpResponse]
}