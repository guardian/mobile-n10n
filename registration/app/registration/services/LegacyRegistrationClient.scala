package registration.services

import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import cats.data.Xor
import cats.implicits._
import models.UniqueDeviceIdentifier
import utils.WSImplicits._

case class LegacyRegistrationClientError(code: Int, message: String)

class LegacyRegistrationClient(wsClient: WSClient, conf: Configuration)(implicit ec: ExecutionContext) {

  def unregister(udid: UniqueDeviceIdentifier): Future[LegacyRegistrationClientError Xor Unit] = {
    wsClient.url(s"${conf.legacyNotficationsEndpoint}/device/registrations/${udid.legacyFormat}")
      .delete()
      .map {
        case r if r.isSuccess => ().right
        case r => LegacyRegistrationClientError(r.status, r.body).left
      }
  }
}
