package com.gu.mobile.notifications.client.models

import org.specs2.mutable.Specification

class LinkTest extends Specification {

  "toString" should {
    "return the url if the Link is of type ExternalLink" in {
      val link = ExternalLink("myLink")
      link.toString mustEqual "myLink"
    }

    "return the correct link if the Link is of type GuardianLinkDetails" in {
      val link = GuardianLinkDetails("myLink1", Some("http://shortUrl"),"myTitle", None, GITContent)
      link.toString mustEqual link.webUrl
    }
  }

}
