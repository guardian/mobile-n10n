package fakebreakingnews

import java.util.UUID
import java.util.concurrent.TimeUnit

import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.mobile.notifications.client.{ApiClient, ApiClientError}
import com.gu.{AppIdentity, AwsIdentity}
import com.typesafe.config.Config
import models.{Android, iOS}
import okhttp3.OkHttpClient
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class FakeBreakingNewsLambda {
  val iosUuid = UUID.fromString("3bf283d8-35f3-48e6-b377-b862c3f030e3")
  val androidUuid = UUID.fromString("a9b4c7cd-1713-4a56-9ada-aa4279dcf534")
  import scala.concurrent.ExecutionContext.Implicits.global
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val config: Config = ConfigurationLoader.load(AppIdentity.whoAmI(defaultAppName = "fake-breaking-news")) {
    case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/fakebreakingnews")
  }
  val okhttp: OkHttpClient = new OkHttpClient()
  val client = ApiClient(
    host = config.getString("notification.notificationHost"),
    apiKey = config.getString("notification.fakeBreakingNewsApiKey"),
    httpProvider = new OkHttpProvider(okhttp)
  )
  val topFrontFetcher = new TopUkRegularStory(okhttp, config.getString("mobileFronts.ukRegularStoriesUrl"))
  val fakeRegistrations = new FakeRegistrations(okhttp, config.getString("registration.legacyDeviceRegistrationUrl"))
  def handleRequest(): Unit = {
    val eventualAndroidRegistration = fakeRegistrations.register(androidUuid, Android)
    val eventualIosRegistration = fakeRegistrations.register(iosUuid, iOS)
    val eventualBreakingNewsPayload = topFrontFetcher.fetchTopFrontAsBreakingNews()
    val futureResult: Future[Either[ApiClientError, Unit]] = for {
      _ <- eventualAndroidRegistration
      _ <- eventualIosRegistration
      breakingNewsNotification <- eventualBreakingNewsPayload
      sendResult <- client.send(breakingNewsNotification)
    } yield sendResult
    val result = Await.result(
      futureResult, Duration(3, TimeUnit.MINUTES))
    logger.info(result.toString)

  }
}
