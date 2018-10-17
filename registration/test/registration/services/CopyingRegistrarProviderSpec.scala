package registration.services

import error.NotificationsError
import metrics.DummyMetrics
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class CopyingRegistrarProviderSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {
  "The CopyingRegistrar" should {
    "wrap a registrar with the CopyingRegistrar" in {
      val copyRegistrar = mock[NotificationRegistrar]
      val mainRegistrar = mock[NotificationRegistrar]
      val delegateRegistrarProvider = new RegistrarProvider {
        override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] = ???
        override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] = ???
        override def registrarFor(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = Right(mainRegistrar)
      }

      val copyingRegistrarProvider = new CopyingRegistrarProvider(delegateRegistrarProvider, copyRegistrar, DummyMetrics)

      val result = copyingRegistrarProvider.registrarFor(iOS, BothTokens("A", "F"), None)
      result should beRight.which { wrappedRegistrar =>
        wrappedRegistrar.providerIdentifier shouldEqual "CopyingRegistrar"
        val copyingRegistrar = wrappedRegistrar.asInstanceOf[CopyingRegistrar]
        copyingRegistrar.mainRegistrar shouldEqual mainRegistrar
        copyingRegistrar.copyRegistrar shouldEqual copyRegistrar
      }
    }
  }
}
