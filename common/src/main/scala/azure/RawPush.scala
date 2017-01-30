package azure

import play.api.libs.ws.{WSRequest, WSResponse}

import scala.concurrent.Future

trait RawPush {

  def tags: Option[Tags]

  def format: String

  def tagQuery: Option[String] = tags.map { set =>
    set.tags.map(_.encodedTag).mkString("(", " || ", ")")
  }

  def extraHeaders: List[(String, String)] = Nil

  def post(request: WSRequest): Future[WSResponse]
}