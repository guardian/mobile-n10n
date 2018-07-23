package registration.services

import models.TopicTypes.NewsstandShard
import models._
import org.specs2.mutable.Specification
import registration.models.LegacyNewsstandRegistration

class LegacyNewsstandRegistrationConverterSpec extends Specification {

  "LegacyNewsstandRegistrationConverter" should {
    "toRegistration" in {
      val pushTokensToShards = Map("pushToken" -> 8, "pushToken3" -> 9)
      val legacyNewsstandRegistrationConverter = new LegacyNewsstandRegistrationConverter(NewsstandShardConfig(10))
      pushTokensToShards.foreach {
        case (pushToken, shardExpected) =>
          val legacyNewsstandRegistration = LegacyNewsstandRegistration(pushToken)
          legacyNewsstandRegistrationConverter.toRegistration(legacyNewsstandRegistration) must beRight(
            Registration(AzureToken(pushToken), Newsstand, Set(Topic(NewsstandShard, s"newsstand-shard-$shardExpected")), None))
      }
      ok
    }
    "fromResponse" in {
      val legacyNewsstandRegistrationConverter = new LegacyNewsstandRegistrationConverter(NewsstandShardConfig(10))
      val inputLegacyNewsstandRegistration = LegacyNewsstandRegistration("pushToken")
      val registrationResponse = RegistrationResponse("deviceId", Newsstand, Set(Topic(NewsstandShard, "newsstand-shard-0")))
      val outputNewstandRegistration: LegacyNewsstandRegistration = legacyNewsstandRegistrationConverter.fromResponse(inputLegacyNewsstandRegistration, registrationResponse)
      outputNewstandRegistration must beEqualTo(inputLegacyNewsstandRegistration)
    }
  }

}
