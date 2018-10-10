package registration.services

import error.NotificationsError
import metrics.DummyMetrics
import models.RegistrationProvider.{Azure, AzureWithFirebase, FCM}
import models.pagination.Paginated
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.specification.mutable.ExecutionEnvironment
import registration.services.NotificationRegistrar.RegistrarResponse

class MigratingRegistrarProviderSpec(implicit ee: ExecutionEnv) extends Specification {

  "Migrating registrar" should {
    "register with azure if an iOS registration has only one Azure Token" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(IOS, AzureToken("a"), None) should beRight(dummyAzureRegistrar)
    }
    "register with firebase if an iOS registration has only one Firebase Token" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(IOS, FcmToken("f"), None) should beRight(dummyFirebaseRegistrar)
    }
    "register with azure if an iOS registration has both tokens, the provider is Azure or unknown" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(IOS, BothTokens("a", "f"), None) should beRight(dummyAzureRegistrar)
      migratingRegistrarProvider.registrarFor(IOS, BothTokens("a", "f"), Some(Azure)) should beRight(dummyAzureRegistrar)
    }
    "migrate to azure if an iOS registration has both tokens, the provider is FCM" in new MigratingRegistrarScope {
      val result = migratingRegistrarProvider.registrarFor(IOS, BothTokens("a", "f"), Some(FCM))
      result should beRight.which(_ should haveClass[MigratingRegistrar])
      result should beRight.which(_.providerIdentifier shouldEqual "FirebaseToAzureRegistrar")
    }

    "migrate to Azure+Firebase if an android registration has only one Firebase Token" in new MigratingRegistrarScope {
      val result = migratingRegistrarProvider.registrarFor(Android, FcmToken("a"), None)
      result should beRight.which(_ should haveClass[MigratingRegistrar])
      result should beRight.which(_.providerIdentifier shouldEqual "FirebaseToAzureWithFirebaseRegistrar")
    }

    "register with Azure+Firebase if an android registration has only one Firebase Token an knows it migrated already" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(Android, FcmToken("a"), Some(AzureWithFirebase)) should beRight(dummyAzureWithFirebaseRegistrar)
    }

    "migrate with Azure+Firebase if an android registration has both tokens an knows it was using Azure only before" in new MigratingRegistrarScope {
      val result = migratingRegistrarProvider.registrarFor(Android, BothTokens("a", "b"), Some(Azure))
      result should beRight.which(_ should haveClass[MigratingRegistrar])
      result should beRight.which(_.providerIdentifier shouldEqual "AzureToAzureWithFirebaseRegistrar")
    }

    "register with Azure only if an android registration has only one Azure Token" in new MigratingRegistrarScope {
      migratingRegistrarProvider.registrarFor(Android, AzureToken("a"), None) should beRight(dummyAzureRegistrar)
    }
  }

  trait MigratingRegistrarScope extends Scope {
    val dummyFirebaseRegistrar = new NotificationRegistrar {
      override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = ???
      override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = ???
      override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
      override val providerIdentifier: String = ""
    }

    val dummyAzureWithFirebaseRegistrar = new NotificationRegistrar {
      override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = ???
      override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = ???
      override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
      override val providerIdentifier: String = ""
    }

    val dummyAzureRegistrar = new NotificationRegistrar {
      override def findRegistrations(topic: Topic, cursor: Option[String]): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def findRegistrations(deviceToken: DeviceToken): RegistrarResponse[List[StoredRegistration]] = ???
      override def findRegistrations(udid: UniqueDeviceIdentifier): RegistrarResponse[Paginated[StoredRegistration]] = ???
      override def unregister(deviceToken: DeviceToken): RegistrarResponse[Unit] = ???
      override def register(deviceToken: DeviceToken, registration: Registration): RegistrarResponse[RegistrationResponse] = ???
      override val providerIdentifier: String = ""
    }

    val dummyAzureRegistrarProvider = new RegistrarProvider {
      override def withAllRegistrars[T](fn: NotificationRegistrar => T): List[T] = ???
      override def registrarFor(registration: Registration): Either[NotificationsError, NotificationRegistrar] = Right(dummyAzureRegistrar)
      override def registrarFor(
        platform: Platform,
        deviceToken: DeviceToken,
        currentProvider: Option[RegistrationProvider]
      ): Either[NotificationsError, NotificationRegistrar] = Right(dummyAzureRegistrar)
    }

    val migratingRegistrarProvider = new MigratingRegistrarProvider(dummyAzureRegistrarProvider, dummyFirebaseRegistrar, dummyAzureWithFirebaseRegistrar, DummyMetrics)
  }
}
