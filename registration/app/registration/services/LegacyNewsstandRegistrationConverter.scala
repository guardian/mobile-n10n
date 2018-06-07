package registration.services

import java.math.BigInteger
import java.nio.ByteBuffer

import error.NotificationsError
import models._
import org.apache.commons.codec.digest.DigestUtils
import registration.models.LegacyNewsstandRegistration

class LegacyNewsstandRegistrationConverter(config: NewsstandShardConfig) extends RegistrationConverter[LegacyNewsstandRegistration] {
  private val shards = config.shards
  def toRegistration(legacyRegistration: LegacyNewsstandRegistration): Either[NotificationsError, Registration] = {
    val udid: NewsstandUdid = NewsstandUdid.fromDeviceToken(legacyRegistration.pushToken)
    val shard = deterministicallyShard(udid, shards)
    Right(Registration(
      deviceId = legacyRegistration.pushToken,
      platform = Newsstand,
      udid = udid,
      topics = Set(Topic(TopicTypes.NewsstandShard, s"newsstand-shard-$shard")),
      buildTier = None
    ))
  }

  private def deterministicallyShard(udid: NewsstandUdid, shards: Long) = {
    val id = udid.id
    val idBytes = ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array()
    val hashInteger = new BigInteger(DigestUtils.getMd5Digest.digest(idBytes))
    hashInteger.mod(BigInteger.valueOf(shards)).intValue()
  }


  def fromResponse(legacyRegistration: LegacyNewsstandRegistration, response: RegistrationResponse): LegacyNewsstandRegistration =
    legacyRegistration
}
