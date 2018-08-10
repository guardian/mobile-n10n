package com.gu.mobile.notifications.client.models

import java.net.URI
import java.util.UUID

import com.gu.mobile.notifications.client.models.TopicTypes._
import com.gu.mobile.notifications.client.models.Topic._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json


class PayloadsSpec extends Specification {


  "NotificationPayload" should {
    def verifySerialization(payload: NotificationPayload, expectedJson: String) = Json.toJson(payload) shouldEqual Json.parse(expectedJson)

    "define serializable Breaking News payload" in {

      val payload = BreakingNewsPayload(
        id = UUID.fromString("30aac5f5-34bb-4a88-8b69-97f995a4907b"),
        title = "The Guardian",
        message = "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
        sender = "test",
        imageUrl = Some(new URI("https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85")),
        thumbnailUrl = Some(new URI("http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg")),
        link = ExternalLink("http://mylink"),
        importance = Importance.Major,
        topic = List(BreakingNewsUk),
        debug = true
      )
      val expectedJson =
        """
          |{
          |  "id" : "30aac5f5-34bb-4a88-8b69-97f995a4907b",
          |  "title" : "The Guardian",
          |  "type" : "news",
          |  "message" : "Mali hotel attack: UN counts 27 bodies as hostage situation ends",
          |  "thumbnailUrl" : "http://media.guim.co.uk/09951387fda453719fe1fee3e5dcea4efa05e4fa/0_181_3596_2160/140.jpg",
          |  "sender": "test",
          |  "link" : {
          |    "url": "http://mylink"
          |  },
          |  "imageUrl" : "https://mobile.guardianapis.com/img/media/a5fb401022d09b2f624a0cc0484c563fd1b6ad93/0_308_4607_2764/master/4607.jpg/6ad3110822bdb2d1d7e8034bcef5dccf?width=800&height=-&quality=85",
          |  "importance" : "Major",
          |  "topic" : [ {
          |    "type" : "breaking",
          |    "name" : "uk"
          |  } ],
          |  "debug":true
          |}
        """.stripMargin

      verifySerialization(payload, expectedJson)
    }

    "define derived id for new article" in new ContentAlertScope {
      payload.derivedId mustEqual ("contentNotifications/newArticle/environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming")
    }

    "define derived id for new liveblog block" in new ContentAlertScope {
      val liveBlogLink = internalLink.copy(blockId = Some("block123456"))
      val liveblogPayload = payload.copy(link = liveBlogLink)
      liveblogPayload.derivedId mustEqual ("contentNotifications/newBlock/environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming/block123456")
    }

    "define serializable Content Alert payload" in new ContentAlertScope{
      val expectedJson =
        """
          |{
          |  "id" : "7c555802-2658-3656-9fda-b4f044a241cc",
          |  "title" : "Follow",
          |  "type" : "content",
          |  "message" : "Which countries are doing the most to stop dangerous global warming?",
          |  "thumbnailUrl" : "http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg",
          |  "sender" : "test",
          |  "link" : {
          |    "contentApiId" : "environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming",
          |    "shortUrl":"http:short.com",
          |    "title":"linkTitle",
          |    "thumbnail":"http://thumb.om",
          |    "git":{"mobileAggregatorPrefix":"item-trimmed"}
          |  },
          |  "importance" : "Minor",
          |  "topic" : [ {
          |    "type" : "tag-series",
          |    "name" : "environment/series/keep-it-in-the-ground"
          |  },{
          |    "type" : "breaking",
          |    "name" : "n2"
          |    }],
          |    "debug" : false
          |}
        """.stripMargin
      verifySerialization(payload, expectedJson)
    }

    "define seriazable goal types" in {
      def verifySerialization(gType: GoalType, expectedJson: String) = Json.toJson(gType) shouldEqual Json.parse(expectedJson)
      verifySerialization(OwnGoalType, "\"Own\"")
      verifySerialization(PenaltyGoalType, "\"Penalty\"")
      verifySerialization(DefaultGoalType, "\"Default\"")
    }
    "define serializable guardian link details" in {
      def verifySerialization(link: GuardianLinkDetails, expectedJson: String) = Json.toJson(link) shouldEqual Json.parse(expectedJson)

      verifySerialization(
        link = GuardianLinkDetails("cApiId", Some("url"), "someTitle", Some("thumb"), GITSection),
        expectedJson = """{"contentApiId":"cApiId","shortUrl":"url","title":"someTitle","thumbnail":"thumb","git":{"mobileAggregatorPrefix":"section"}}"""
      )

      verifySerialization(
        link = GuardianLinkDetails("cApiId", Some("url"), "someOtherTitle", Some("thumb"), GITTag),
        expectedJson = """{"contentApiId":"cApiId","shortUrl":"url","title":"someOtherTitle","thumbnail":"thumb","git":{"mobileAggregatorPrefix":"latest"}}"""
      )

      verifySerialization(
        link = GuardianLinkDetails("cApiId", Some("url"), "someTitle", Some("thumb"), GITContent),
        expectedJson = """{"contentApiId":"cApiId","shortUrl":"url","title":"someTitle","thumbnail":"thumb","git":{"mobileAggregatorPrefix":"item-trimmed"}}"""
      )

    }
  }
}

trait ContentAlertScope extends Scope {

  val internalLink = GuardianLinkDetails(
    contentApiId = "environment/ng-interactive/2015/oct/16/which-countries-are-doing-the-most-to-stop-dangerous-global-warming",
    shortUrl = Some("http:short.com"),
    title = "linkTitle",
    thumbnail = Some("http://thumb.om"),
    git = GITContent)

  val payload = ContentAlertPayload(
    title = "Follow",
    message = "Which countries are doing the most to stop dangerous global warming?",
    thumbnailUrl = Some(new URI("http://media.guim.co.uk/a07334e4ed5d13d3ecf4c1ac21145f7f4a099f18/127_0_3372_2023/140.jpg")),
    sender = "test",
    link = internalLink,
    importance = Importance.Minor,
    topic = List(Topic(TagSeries, "environment/series/keep-it-in-the-ground"), Topic(Breaking, "n2")),
    debug = false)

}
