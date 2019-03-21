package fakebreakingnews

import java.util.UUID

import models.{Android, Platform, iOS}
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.apache.logging.log4j.LogManager
import play.api.libs.json.Json

import scala.concurrent.Future

case class FakeRegistrationDevice(
  platform: String,
  firebaseToken: Option[String],
  pushToken: Option[String],
  versionNumber: String = "version",
  buildTier: String = "buildtier"
)

object FakeRegistrationDevice {
  implicit val fakeRegistrationDeviceJF = Json.format[FakeRegistrationDevice]
}

case class FakeRegistrationTopic(
  `type`: String = "breaking",
  name: String = "internal-test",
  title: String = "internal-test",
)

object FakeRegistrationTopic {
  implicit val fakeRegistrationTopicJF = Json.format[FakeRegistrationTopic]
}

case class FakeRegistrationPreferences(
  topics: List[FakeRegistrationTopic] = List(FakeRegistrationTopic()),
  edition: String = "uk",
  receiveNewsAlerts: Boolean = false
)

object FakeRegistrationPreferences {
  implicit val fakeRegistrationPreferencesJF = Json.format[FakeRegistrationPreferences]
}

case class FakeRegistration(
  device: FakeRegistrationDevice,
  preferences: FakeRegistrationPreferences = FakeRegistrationPreferences()

)

object FakeRegistration {
  implicit val fakeRegistrationJf = Json.format[FakeRegistration]
}

class FakeRegistrations(okHttpClient: OkHttpClient, legacyDeviceRegistrationUrl: String) {
  val logger = LogManager.getLogger(classOf[FakeRegistrations])

  def register(uuid: UUID, platform: Platform): Future[Unit] = {
    val firebaseToken: Option[String] = if (platform == Android) Some(s"token-for-firebase-$uuid") else None
    val pushToken: Option[String] = if (platform == iOS) Some(s"token-for-push-$uuid") else None
    val body = Json.toJson(FakeRegistration(FakeRegistrationDevice(platform.toString, firebaseToken, pushToken)))
    val request = new Request.Builder()
      .url(legacyDeviceRegistrationUrl)
      .post(RequestBody.create(MediaType.get("application/json; charset=UTF-8"), Json.toBytes(body)))
      .build()
    RequestToPromise.requestToPromise(okHttpClient, request, (code, _) => {
      if (code < 200 || code >= 300) {
        throw new Exception(s"Unexpected code $code for request ${request.method()} ${request.url()}")
      }
    })
  }
}


