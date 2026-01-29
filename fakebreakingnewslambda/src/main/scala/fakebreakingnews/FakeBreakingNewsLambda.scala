package fakebreakingnews

import java.util.UUID
import java.util.concurrent.TimeUnit
import com.typesafe.config.Config
import models.{Android, Ios}
import okhttp3.OkHttpClient
import org.slf4j.{Logger, LoggerFactory}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity, DevIdentity}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region.EU_WEST_1

import javax.net.ssl.{HostnameVerifier, SSLSession}

class FakeBreakingNewsLambda {
  val iosUuid = UUID.fromString("3bf283d8-35f3-48e6-b377-b862c3f030e3")
  val androidUuid = UUID.fromString("a9b4c7cd-1713-4a56-9ada-aa4279dcf534")
  import scala.concurrent.ExecutionContext.Implicits.global
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private def getIdentity(defaultAppName: String): AppIdentity = {
    Option(System.getenv("MOBILE_LOCAL_DEV")) match {
      case Some(_) => DevIdentity(defaultAppName)
      case None =>
        AppIdentity
          .whoAmI(defaultAppName, DefaultCredentialsProvider.builder().build())
          .getOrElse(DevIdentity(defaultAppName))
    }
  }
  private val config: Config = ConfigurationLoader.load(getIdentity(defaultAppName = "fake-breaking-news")) {
    case AwsIdentity(_, _, stage, region) =>
      SSMConfigurationLocation(s"/notifications/$stage/fakebreakingnews", region)
  }
  val okhttp = new OkHttpClient()
  val notificationApiOkHttp: OkHttpClient = new OkHttpClient()
    .newBuilder()
    .hostnameVerifier(new HostnameVerifier() {
      override def verify(hostname: String, session: SSLSession): Boolean = hostname.toLowerCase match {
        // Trust the ALB's DNS name for testing purposes
        case "mobile-loadb-02xt267ga6y8-1312305848.eu-west-1.elb.amazonaws.com" => true // CODE
        case "mobile-loadb-vv0gvmydrkop-56640827.eu-west-1.elb.amazonaws.com" => true // PROD
        case _ => false
      }
    })
    .build()
  val client = new NotificationClient(
    okhttp = notificationApiOkHttp,
    host = config.getString("notification.notificationHost"),
    apiKey = config.getString("notification.fakeBreakingNewsApiKey")
  )
  val topFrontFetcher = new TopUkRegularStory(okhttp, config.getString("mobileFronts.ukRegularStoriesUrl"))
  val fakeRegistrations = new FakeRegistrations(okhttp, config.getString("registration.legacyDeviceRegistrationUrl"))
  def handleRequest(): Unit = {
    val eventualAndroidRegistration = fakeRegistrations.register(androidUuid, Android)
    val eventualIosRegistration = fakeRegistrations.register(iosUuid, Ios)
    val eventualBreakingNewsPayload = topFrontFetcher.fetchTopFrontAsBreakingNews()
    val futureResult: Future[String] = for {
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
