package azure

import azure.apns.Body
import play.api.http.Writeable
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSRequest, WSResponse}
import utils.WriteableImplicits._

import scala.concurrent.Future

case class APNSRawPush(body: Body, tags: Option[Tags]) extends RawPush {
  override def format: String = "apple"

  private val writeable: Writeable[Body] = implicitly[Writeable[JsValue]].map(Json.toJson[Body]).withContentType("application/json;charset=utf-8")

  override def post(request: WSRequest): Future[WSResponse] = request.post(body)(writeable)
}
