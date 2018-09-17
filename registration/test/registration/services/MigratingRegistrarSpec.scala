package registration.services

import models.pagination.Paginated
import models.{DeviceToken, Registration, Topic, UniqueDeviceIdentifier}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import registration.services.fcm.FcmRegistrar

import scala.concurrent.Future

class MigratingRegistrarSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {

  "The migrating registrar" should {
    "migrate users from azure to firebase when the device registers" in new MigratingRegistrarScope {
      val fcmRegistrationResponse = mock[RegistrationResponse]
      val deviceToken = mock[DeviceToken]
      val registration = mock[Registration]
      fcmRegistrar.register(deviceToken, registration) returns Future.successful(Right(fcmRegistrationResponse))
      azureRegistrar.unregister(deviceToken) returns Future.successful(Right(()))

      val response = migratingRegistrar.register(deviceToken, registration)

      response should beRight(fcmRegistrationResponse).await
      there was one(azureRegistrar).unregister(deviceToken)
      there was one(fcmRegistrar).register(deviceToken, registration)
    }

    "unregister from both azure and firebase" in new MigratingRegistrarScope {
      val deviceToken = mock[DeviceToken]
      fcmRegistrar.unregister(deviceToken) returns Future.successful(Right(()))
      azureRegistrar.unregister(deviceToken) returns Future.successful(Right(()))

      val response = migratingRegistrar.unregister(deviceToken)

      response should beRight(()).await
      there was one(azureRegistrar).unregister(deviceToken)
      there was one(fcmRegistrar).unregister(deviceToken)
    }

    "only find registration by topic in azure as it's not possible in firebase" in new MigratingRegistrarScope {
      val topic = mock[Topic]
      val findResponse = mock[Paginated[StoredRegistration]]
      azureRegistrar.findRegistrations(topic, None) returns Future.successful(Right(findResponse))

      val response = migratingRegistrar.findRegistrations(topic, None)

      response should beRight(findResponse).await
      there was no(fcmRegistrar).findRegistrations(topic, None)
      there was one(azureRegistrar).findRegistrations(topic, None)
    }

    "only find registration by udid in azure as it's not possible in firebase" in new MigratingRegistrarScope {
      val udid = mock[UniqueDeviceIdentifier]
      val findResponse = mock[Paginated[StoredRegistration]]
      azureRegistrar.findRegistrations(udid) returns Future.successful(Right(findResponse))

      val response = migratingRegistrar.findRegistrations(udid)

      response should beRight(findResponse).await
      there was no(fcmRegistrar).findRegistrations(udid)
      there was one(azureRegistrar).findRegistrations(udid)
    }

    "find registration from both firebase and azure, and concatenate the results" in new MigratingRegistrarScope {
      val token = mock[DeviceToken]
      val firebaseRegistration = mock[StoredRegistration]
      val azureRegistration = mock[StoredRegistration]
      val expectedResponse = List(firebaseRegistration, azureRegistration)

      azureRegistrar.findRegistrations(token) returns Future.successful(Right(List(azureRegistration)))
      fcmRegistrar.findRegistrations(token) returns Future.successful(Right(List(firebaseRegistration)))


      val response = migratingRegistrar.findRegistrations(token)

      response should beRight(expectedResponse).await
      there was one(fcmRegistrar).findRegistrations(token)
      there was one(azureRegistrar).findRegistrations(token)
    }


  }

  trait MigratingRegistrarScope extends Scope {

    val fcmRegistrar = mock[FcmRegistrar]
    val azureRegistrar = mock[NotificationRegistrar]
    val migratingRegistrar = new MigratingRegistrar(
      providerIdentifier = "testRegistrar",
      fromRegistrar = azureRegistrar,
      toRegistrar = fcmRegistrar
    )
  }

}
