package azure

import azure.apns.Body
import play.api.libs.ws.{WSRequest, WSResponse}

import scala.concurrent.Future

case class APNSRawPush(body: Body, tags: Option[Tags]) extends RawPush {
  override def format: String = "apple"

  implicit val writer = implicitly(bodyWritable[Body])

  override def post(request: WSRequest): Future[WSResponse] = request.post[Body](body)//(writeable)
}
