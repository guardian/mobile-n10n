package fakebreakingnews

import java.util.concurrent.TimeUnit

import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.mobile.notifications.client.ApiClient
import com.gu.{AppIdentity, AwsIdentity}
import com.typesafe.config.Config
import okhttp3.OkHttpClient
import org.apache.logging.log4j.{LogManager, Logger}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class FakeBreakingNewsLambda {
  import scala.concurrent.ExecutionContext.Implicits.global
  private val logger: Logger = LogManager.getLogger(classOf[FakeBreakingNewsLambda])
  private val config: Config = ConfigurationLoader.load(AppIdentity.whoAmI(defaultAppName = "fake-breaking-news")) {
    case AwsIdentity(_, _, stage, _) => SSMConfigurationLocation(s"/notifications/$stage/fakebreakingnews")
  }
  val okhttp: OkHttpClient = new OkHttpClient()
  val client = ApiClient(
    host = config.getString("notification.notificationHost"),
    apiKey = config.getString("notification.fakeBreakingNewsApiKey"),
    httpProvider = new OkHttpProvider(okhttp)
  )
  val topFrontFetcher = new TopUkRegularStory(okhttp, config.getString("mapi.ukRegularStoriesUrl"))

  def handleRequest(): Unit = {
    val result = Await.result(
      for {
        breakingNewsNotification <- topFrontFetcher.fetchTopFrontAsBreakingNews
        sendResult <- client.send(breakingNewsNotification)
      } yield (sendResult), Duration(3, TimeUnit.MINUTES))
    logger.info(result)

  }
}
