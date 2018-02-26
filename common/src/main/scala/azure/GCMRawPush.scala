package azure

import play.api.http.Writeable
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSRequest, WSResponse}
import utils.WriteableImplicits._

import scala.concurrent.Future

case class GCMBody(
  collapse_key: Option[String] = None,
  time_to_live: Option[String] = None,
  delay_while_idle: Option[Boolean] = None,
  data: Map[String, String]
)
object GCMBody {
  implicit val jf = Json.format[GCMBody]
}

case class GCMRawPush(body: GCMBody, tags: Option[Tags]) extends RawPush {
  override def format: String = "gcm"

  private val writeable: Writeable[GCMBody] = implicitly[Writeable[JsValue]].map(Json.toJson[GCMBody]).withContentType("application/json;charset=utf-8")

  override def post(request: WSRequest): Future[WSResponse] = request.post(body)(writeable)
}
