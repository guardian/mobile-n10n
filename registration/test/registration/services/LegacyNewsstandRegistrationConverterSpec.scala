package registration.services

import java.util.UUID

import models.TopicTypes.NewsstandShard
import models._
import org.specs2.mutable.Specification
import registration.models.LegacyNewsstandRegistration

class LegacyNewsstandRegistrationConverterSpec extends Specification {

  "LegacyNewsstandRegistrationConverter" should {
    "toRegistration" in {
      val pushTokensToShards = Map("pushToken" -> 7, "pushToken3" -> 0)
      val legacyNewsstandRegistrationConverter = new LegacyNewsstandRegistrationConverter(NewsstandShardConfig(10))
      pushTokensToShards.foreach {
        case (pushToken, shardExpected) =>
          val legacyNewsstandRegistration = LegacyNewsstandRegistration(pushToken)
          legacyNewsstandRegistrationConverter.toRegistration(legacyNewsstandRegistration) must beRight(
            Registration(pushToken, Newsstand, NewsstandUdid(pushToken), Set(Topic(NewsstandShard, s"newsstand-shard-$shardExpected")), None))
      }
      ok
    }
    "toRegistration" in {
      val legacyNewsstandRegistrationConverter = new LegacyNewsstandRegistrationConverter(NewsstandShardConfig(10))
      val inputLegacyNewsstandRegistration = LegacyNewsstandRegistration("pushToken")
      val registrationResponse = RegistrationResponse("deviceId", Newsstand, UniqueDeviceIdentifier(UUID.randomUUID()), Set(Topic(NewsstandShard, "newsstand-shard-0")))
      val outputNewstandRegistration: LegacyNewsstandRegistration = legacyNewsstandRegistrationConverter.fromResponse(inputLegacyNewsstandRegistration, registrationResponse)
      outputNewstandRegistration must beEqualTo(inputLegacyNewsstandRegistration)
    }
  }

}
