package azure

import play.api.libs.json.Json
import play.api.libs.ws.{WSRequest, WSResponse}

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

case class GCMRawPush(body: GCMBody, tags: Option[Tags]) extends RawPush[GCMBody] {
  override def format: String = "gcm"

  implicit val writer = implicitly(bodyWritable)

  override def post(request: WSRequest): Future[WSResponse] = request.post[GCMBody](body)
}
