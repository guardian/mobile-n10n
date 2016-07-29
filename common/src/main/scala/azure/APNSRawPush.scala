package azure

import azure.apns.Body
import play.api.http.Writeable
import play.api.libs.json.{Json, JsValue}
import utils.WriteableImplicits._

case class APNSRawPush(body: Body, tags: Option[Tags]) extends RawPush[Body] {
  override def format: String = "apple"

  override def writeable: Writeable[Body] = implicitly[Writeable[JsValue]].map(Json.toJson[Body]).withContentType("application/json;charset=utf-8")
}
