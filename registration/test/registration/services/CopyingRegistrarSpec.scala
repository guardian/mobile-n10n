package registration.services

import models.pagination.Paginated
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import registration.services.fcm.FcmRegistrar

import scala.concurrent.Future

class CopyingRegistrarSpec(implicit ee: ExecutionEnv) extends Specification with Mockito {

  "The copying registrar" should {
    "register devices with the main and copy registrars" in new CopyingRegistrarScope {
      val registrationResponse = mock[RegistrationResponse]
      val deviceToken = mock[DeviceToken]
      val registration = mock[Registration]
      mainRegistrar.register(deviceToken, registration) returns Future.successful(Right(registrationResponse))
      copyRegistrar.register(deviceToken, registration) returns Future.successful(Right(registrationResponse))

      val response = copyingRegistrar.register(deviceToken, registration)

      response should beRight(registrationResponse).await
      there was one(copyRegistrar).register(deviceToken, registration)
      there was one(mainRegistrar).register(deviceToken, registration)
    }

    "unregister devices using both the main and copy registrars" in new CopyingRegistrarScope {
      val deviceToken = mock[DeviceToken]
      mainRegistrar.unregister(deviceToken, iOS) returns Future.successful(Right(()))
      copyRegistrar.unregister(deviceToken, iOS) returns Future.successful(Right(()))

      val response = copyingRegistrar.unregister(deviceToken, iOS)

      response should beRight(()).await
      there was one(copyRegistrar).unregister(deviceToken, iOS)
      there was one(mainRegistrar).unregister(deviceToken, iOS)
    }

    "only search registrations by topic using the main registrar" in new CopyingRegistrarScope {
      val topic = mock[Topic]
      val findResponse = mock[Paginated[StoredRegistration]]
      mainRegistrar.findRegistrations(topic, None) returns Future.successful(Right(findResponse))

      val response = copyingRegistrar.findRegistrations(topic, None)

      response should beRight(findResponse).await
      there was one(mainRegistrar).findRegistrations(topic, None)
      there was no(copyRegistrar).findRegistrations(topic, None)
    }

    "only find registration by udid in using the main registrar" in new CopyingRegistrarScope {
      val udid = mock[UniqueDeviceIdentifier]
      val findResponse = mock[Paginated[StoredRegistration]]
      mainRegistrar.findRegistrations(udid) returns Future.successful(Right(findResponse))

      val response = copyingRegistrar.findRegistrations(udid)

      response should beRight(findResponse).await
      there was one(mainRegistrar).findRegistrations(udid)
      there was no(copyRegistrar).findRegistrations(udid)
    }

    "find registration using both the main and copy registrar, returning concatenated results" in new CopyingRegistrarScope {
      val token = mock[DeviceToken]
      val mainRegistration = mock[StoredRegistration]
      val copyRegistration = mock[StoredRegistration]
      val expectedResponse = List(mainRegistration, copyRegistration)

      copyRegistrar.findRegistrations(token, iOS) returns Future.successful(Right(List(copyRegistration)))
      mainRegistrar.findRegistrations(token, iOS) returns Future.successful(Right(List(mainRegistration)))


      val response = copyingRegistrar.findRegistrations(token, iOS)

      response should beRight(expectedResponse).await
      there was one(mainRegistrar).findRegistrations(token, iOS)
      there was one(copyRegistrar).findRegistrations(token, iOS)
    }


  }

  trait CopyingRegistrarScope extends Scope {

    val mainRegistrar = mock[FcmRegistrar]
    val copyRegistrar = mock[NotificationRegistrar]
    val copyingRegistrar = new CopyingRegistrar(
      providerIdentifier = "testRegistrar",
      azureRegistrar = mainRegistrar,
      databaseRegistrar = copyRegistrar
    )
  }

}
