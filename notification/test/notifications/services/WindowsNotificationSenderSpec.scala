package notifications.services

import java.util.UUID

import azure.RawWindowsRegistration.fromMobileRegistration
import azure.{NotificationHubClient, RawWindowsRegistration, RegistrationResponse => HubRegistrationResponse, WNSRegistrationId}
import models.{Registration, UserId, WindowsMobile}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future
import scalaz.syntax.either._

class WindowsNotificationSenderSpec(implicit ev: ExecutionEnv) extends Specification
with Mockito {


}
