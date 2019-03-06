package auditor

import models.Topic
import models.TopicTypes.{TagKeyword, TagSeries}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class TimeExpiringAuditorSpec(implicit ee: ExecutionEnv) extends Specification {
  "A TimeExpiryTopic" should {
    "Expiry reference topics if date is after threshold" in new TimeExpiringScope {
      val validator = TimeExpiringAuditor(referenceTopics, DateTime.now.minusHours(1))
      validator.expiredTopics(referenceTopics) must beEqualTo(referenceTopics).await
    }

    "Not expire reference topics before the threshold" in new TimeExpiringScope {
      val validator = TimeExpiringAuditor(referenceTopics, DateTime.now.plusHours(1))
      validator.expiredTopics(referenceTopics) must beEqualTo(Set.empty).await
    }

    "Not expire topics that are not in the set" in new TimeExpiringScope {
      val validator = TimeExpiringAuditor(referenceTopics, DateTime.now.minusHours(1))
      validator.expiredTopics(referenceTopics ++ otherTopics) must beEqualTo(referenceTopics).await
    }


  }

  trait TimeExpiringScope extends Scope {
    val referenceTopics = Set(
      Topic(TagSeries, "test-series-1"),
      Topic(TagKeyword, "test-series-1")
    )

    val otherTopics = Set(
      Topic(TagSeries, "test-series-2"),
      Topic(TagKeyword, "test-series-2")
    )
  }
}
