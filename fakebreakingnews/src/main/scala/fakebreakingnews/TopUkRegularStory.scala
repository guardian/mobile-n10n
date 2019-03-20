package fakebreakingnews

import java.io.IOException
import java.util.UUID

import com.gu.mobile.notifications.client
import com.gu.mobile.notifications.client.models.{BreakingNewsPayload, GuardianLinkDetails}
import okhttp3.{Call, Callback, OkHttpClient, Request, Response}
import play.api.libs.json.Json

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

case class UkRegularStoryLinks(shortUrl: Option[String])

object UkRegularStoryLinks {
  implicit val ukRegularStoryLinksJF = Json.format[UkRegularStoryLinks]
}

case class UkRegularStoryItem(id: String, links: UkRegularStoryLinks)

object UkRegularStoryItem {
  implicit val ukRegularStoryItemJF = Json.format[UkRegularStoryItem]
}

case class UkRegularStoriesCard(item: UkRegularStoryItem, title: String)

object UkRegularStoriesCard {
  implicit val ukRegularStoriesCardJF = Json.format[UkRegularStoriesCard]
}

case class UkRegularStories(cards: List[UkRegularStoriesCard])

object UkRegularStories {
  implicit val ukRegularStoriesJF = Json.format[UkRegularStories]
}

class TopUkRegularStory(okHttpClient: OkHttpClient, ukRegularStoriesUrl: String) {
  def fetchTopFrontAsBreakingNews: Future[BreakingNewsPayload] = {

    val promise = Promise[BreakingNewsPayload]
    val call = okHttpClient.newCall(new Request.Builder().build())
    call.enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = promise.failure(new Exception(s"Error getting $ukRegularStoriesUrl", e))

      override def onResponse(call: Call, response: Response): Unit = Try {
        val maybebreakingNewsNotification = for {
          body <- Option(response.body)
          bodyString = body.string()
          bodyJson = Json.parse(bodyString)
          firstUkRegularStory = bodyJson.as[UkRegularStories].cards(0)
          breakingNewsPayload = BreakingNewsPayload(
            id = UUID.randomUUID(),
            title = firstUkRegularStory.title,
            message = firstUkRegularStory.title,
            thumbnailUrl = None,
            sender = "newstester",
            link = GuardianLinkDetails(
              contentApiId = firstUkRegularStory.item.id,
              shortUrl = firstUkRegularStory.item.links.shortUrl,
              firstUkRegularStory.title,
              thumbnail = None,
              git = client.models.GITContent,
              blockId = None
            ),

            imageUrl = None,
            importance = client.models.Importance.Major,
            topic = List(client.models.Topic.BreakingNewsInternalTest),
            debug = false

          )
        } yield breakingNewsPayload
        maybebreakingNewsNotification.get
      } match {
        case Success(value) => promise.success(value)
        case Failure(e) => promise.failure(new Exception(s"Error getting $ukRegularStoriesUrl", e))
      }
    })
    promise.future
  }

}
