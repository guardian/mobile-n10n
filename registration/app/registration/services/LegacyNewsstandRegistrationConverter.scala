package registration.services

import java.math.BigInteger
import java.nio.charset.StandardCharsets

import error.NotificationsError
import models._
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.{Logger, LoggerFactory}
import registration.models.LegacyNewsstandRegistration

class LegacyNewsstandRegistrationConverter(config: NewsstandShardConfig) extends RegistrationConverter[LegacyNewsstandRegistration] {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)
  private val shards = config.shards
  def toRegistration(legacyRegistration: LegacyNewsstandRegistration): Either[NotificationsError, Registration] = {
    val shard = deterministicallyShard(legacyRegistration.pushToken, shards)
    val newstandShardTopic = s"newsstand-shard-$shard"
    logger.info(s"User registered to Newsstand Shard Topic: $newstandShardTopic")
    Right(Registration(
      deviceToken = DeviceToken(legacyRegistration.pushToken),
      platform = Newsstand,
      topics = Set(Topic(TopicTypes.NewsstandShard, newstandShardTopic)),
      buildTier = None,
      appVersion = None
    ))
  }

  private def deterministicallyShard(pushToken:String , shards: Long) = {
    val idBytes = pushToken.getBytes(StandardCharsets.UTF_8)
    val hashInteger = new BigInteger(DigestUtils.getMd5Digest.digest(idBytes))
    hashInteger.mod(BigInteger.valueOf(shards)).intValue()
  }


  def fromResponse(legacyRegistration: LegacyNewsstandRegistration, response: RegistrationResponse): LegacyNewsstandRegistration =
    legacyRegistration
}
