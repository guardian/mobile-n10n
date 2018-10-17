package registration.services

import error.NotificationsError
import metrics.DummyMetrics
import models.Provider.{Azure, FCM}
import models.pagination.Paginated
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import registration.services.NotificationRegistrar.RegistrarResponse

class MigratingRegistrarProviderSpec(implicit ee: ExecutionEnv) extends Specification {

  "Migrating registrar" should {
    "register with azure if an iOS registration has only one Azure Token" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(iOS, AzureToken("a"), None) should beRight(dummyAzureRegistrar)
    }
    "register with firebase if an iOS registration has only one Firebase Token" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(iOS, FcmToken("f"), None) should beRight(dummyFirebaseRegistrar)
    }
    "register with azure if an iOS registration has both tokens, the provider is Azure or unknown" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(iOS, BothTokens("a", "f"), None) should beRight(dummyAzureRegistrar)
      migratingRegistrarProvider.registrarFor(iOS, BothTokens("a", "f"), Some(Azure)) should beRight(dummyAzureRegistrar)
    }
    "migrate to azure if an iOS registration has both tokens, the provider is FCM" in new MigratingRegistrarScope {
      val result = migratingRegistrarProvider.registrarFor(iOS, BothTokens("a", "f"), Some(FCM))
      result should beRight.which(_ should haveClass[MigratingRegistrar])
      result should beRight.which(_.providerIdentifier shouldEqual "IosFirebaseToAzureRegistrar")
    }

    "register with azure if an Android registration has only one Azure Token" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(Android, AzureToken("a"), None) should beRight(dummyAzureRegistrar)
    }
    "migrate to azure if an Android registration has only one Firebase Token" in new MigratingRegistrarScope {
      val result = migratingRegistrarProvider.registrarFor(Android, FcmToken("f"), None)
      result should beRight.which(_ should haveClass[MigratingRegistrar])
      result should beRight.which(_.providerIdentifier shouldEqual "AndroidFirebaseToAzureRegistrar")
    }
    "migrate to azure if an Android registration has both tokens, the provider is Azure or unknown" in new MigratingRegistrarScope {
      val result1 = migratingRegistrarProvider.registrarFor(Android, BothTokens("a", "f"), None)
      val result2 = migratingRegistrarProvider.registrarFor(Android, BothTokens("a", "f"), Some(Azure))
      result1 should beRight.which(_ should haveClass[MigratingRegistrar])
      result1 should beRight.which(_.providerIdentifier shouldEqual "AndroidFirebaseToAzureRegistrar")
      result2 should beRight.which(_ should haveClass[MigratingRegistrar])
      result2 should beRight.which(_.providerIdentifier shouldEqual "AndroidFirebaseToAzureRegistrar")
    }
    "migrate to azure if an Android registration has both tokens, the provider is FCM" in new MigratingRegistrarScope {
      val result = migratingRegistrarProvider.registrarFor(Android, BothTokens("a", "f"), Some(FCM))
      result should beRight.which(_ should haveClass[MigratingRegistrar])
      result should beRight.which(_.providerIdentifier shouldEqual "AndroidFirebaseToAzureRegistrar")
    }
  }

  trait MigratingRegistrarScope extends Scope {
    val dummyFirebaseRegistrar = new NotificationRegistrar {
      override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def findRegistrations(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[List[StoredRegistration]] = ???
      override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def unregister(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[Unit] = ???
      override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
      override val providerIdentifier: String = ""
    }

    val dummyAzureRegistrar = new NotificationRegistrar {
      override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def findRegistrations(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[List[StoredRegistration]] = ???
      override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def unregister(deviceToken: DeviceToken, platform: Platform): RegistrarResponse[Unit] = ???
      override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
      override val providerIdentifier: String = ""
    }

    val dummyAzureRegistrarProvider = new RegistrarProvider {
      override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] = ???
      override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] = Right(dummyAzureRegistrar)
      override def registrarFor(platform: Platform, deviceToken: DeviceToken, currentProvider: Option[Provider]): Either[NotificationsError, NotificationRegistrar] = Right(dummyAzureRegistrar)
    }

    val migratingRegistrarProvider = new MigratingRegistrarProvider(dummyAzureRegistrarProvider, dummyFirebaseRegistrar, DummyMetrics)
  }
}
