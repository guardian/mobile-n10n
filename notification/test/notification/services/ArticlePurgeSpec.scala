package notification.services

import models.GITContent
import models.Link.Internal
import notification.NotificationsFixtures
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future

class ArticlePurgeSpec(implicit ee: ExecutionEnv) extends Specification with Mockito with NotificationsFixtures {

  "ArticlePurge" should {
    "trigger a soft purge for a breaking news" in new ArticlePurgeScope {
      val breakingNews = breakingNewsNotification(Nil).copy(link = new Internal("expected/article/id", None, GITContent, None))

      articlePurge.purgeFromNotification(breakingNews) should beEqualTo(true).await
      val urlCaptor = capture[String]
      there was one(fastlyPurge).softPurge(urlCaptor)
      println(urlCaptor.value)
      urlCaptor.value shouldEqual "expected/article/id"
    }

    "do nothing for any other notification type" in new ArticlePurgeScope {
      val newsstand = newsstandShardNotification()
      articlePurge.purgeFromNotification(newsstand) should beEqualTo(false).await
      there was no(fastlyPurge).softPurge(any[String])
    }
  }

  trait ArticlePurgeScope extends Scope {

    val fastlyPurge: FastlyPurge = {
      val m = mock[FastlyPurge]
      m.softPurge(any[String]) returns Future.successful(true)
      m
    }

    val articlePurge = new ArticlePurge(fastlyPurge)

  }

}
