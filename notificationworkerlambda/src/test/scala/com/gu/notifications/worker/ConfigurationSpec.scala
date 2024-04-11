package com.gu.notifications.worker

import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import com.gu.notifications.worker.delivery.fcm.models.FcmConfig

class FcmWorkerConfigurationSpec extends Specification with Matchers {

  "FcmConfiguration" should {
    "use individual send API if the topic is in the selected list" in new TestDataScope {
      val config = createConfiguration(List("breaking/uk"))
      config.isIndividualSend(List("breaking/uk")) must beTrue
    }

    "use individual send API if the prefix of topic is in the selected list" in new TestDataScope {
      val config = createConfiguration(List("breaking/"))
      config.isIndividualSend(List("breaking/uk")) must beTrue
    }

    "use batch send API if the topic is not in the selected list" in new TestDataScope {
      val config = createConfiguration(List("breaking/uk"))
      config.isIndividualSend(List("breaking/international")) must beFalse
    }

    "use batch send API if the prefix of topic is not in the selected list" in new TestDataScope {
      val config = createConfiguration(List("breaking/"))
      config.isIndividualSend(List("contributor/")) must beFalse
    }

    "use individual send API if the prefix of topic matches one of the selected topics" in new TestDataScope {
      val config = createConfiguration(List("breaking/", "contributor/"))
      config.isIndividualSend(List("breaking/uk")) must beTrue
    }

    "use batch send API if the prefix of topic matches none of the selected topics" in new TestDataScope {
      val config = createConfiguration(List("breaking/", "contributor/"))
      config.isIndividualSend(List("tag/food")) must beFalse
    }

    "use individual send API if the prefix of every topic matches one of the selected topics" in new TestDataScope {
      val config = createConfiguration(List("breaking/", "contributor/"))
      config.isIndividualSend(List("breaking/uk", "breaking/us", "breaking/international")) must beTrue
    }

    "use batch send API if the prefix of any topic do not match one of the selected topics" in new TestDataScope {
      val config = createConfiguration(List("tag/", "contributor/"))
      config.isIndividualSend(List("tag/food", "tag/culture", "breaking/international")) must beFalse
    }
  }

  trait TestDataScope extends Scope {
    def createConfiguration(selectedTopics: List[String]): FcmWorkerConfiguration =
      FcmWorkerConfiguration(
        cleaningSqsUrl = "cleaning-sqs-url",
        fcmConfig = FcmConfig(serviceAccountKey = "key", debug = false, dryRun = false),
        threadPoolSize = 50,
        allowedTopicsForIndividualSend = selectedTopics)
  }
}