package registration.services

import play.api.libs.ws.WSClient
import registration.models.LegacyRegistration

import scala.concurrent.{ExecutionContext, Future}
import scalaz.\/
import scalaz.syntax.either._
import utils.WSImplicits._

case class LegacyRegistrationClientError(code: Int, message: String)

class LegacyRegistrationClient(wsClient: WSClient, conf: Configuration)(implicit ec: ExecutionContext) {

  def unregister(registration: LegacyRegistration): Future[LegacyRegistrationClientError \/ Unit] = {
    wsClient.url(s"${conf.legacyNotficationsEndpoint}/device/registrations/${registration.device.udid}")
      .delete()
      .map {
        case r if r.isSuccess => ().right
        case r => LegacyRegistrationClientError(r.status, r.body).left
      }
  }
}
