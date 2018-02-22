package azure

import akka.stream.scaladsl.JavaFlowSupport
import akka.util.ByteString
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSRequest, WSResponse}
import play.api.libs.json.{Format, Json}

import scala.concurrent.Future

trait RawPush {

  implicit def bodyWritable[T]()(implicit format: Format[T]): BodyWritable[T] = {
    def trasform(t: T) = {
      InMemoryBody(ByteString.fromString(Json.toJson(t).toString()))
    }
    BodyWritable(trasform, "application/json;charset=utf-8")
  }

  def tags: Option[Tags]

  def format: String

  def tagQuery: Option[String] = tags.map { set =>
    set.tags.map(_.encodedTag).mkString("(", " || ", ")")
  }

  def extraHeaders: List[(String, String)] = Nil

  def post(request: WSRequest): Future[WSResponse]
}