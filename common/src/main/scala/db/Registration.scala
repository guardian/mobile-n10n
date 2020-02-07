package db

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDateTime

import doobie.util.Meta
import models.{Android, DeviceToken, Platform}

object Registration {
  implicit val PlatformMeta: Meta[Platform] =
    Meta[String].timap(
      s => Platform.fromString(s).getOrElse(throw doobie.util.invariant.InvalidEnum[Platform](s)),
    )(_.toString)

  implicit val DateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].timap(ts => ts.toLocalDateTime)(dt => Timestamp.valueOf(dt))
}

case class Registration(device: Device, topic: Topic, shard: Shard, lastModified: Option[LocalDateTime] = None, buildTier: Option[BuildTier])
case class Device(token: String, platform: Platform)
case class Topic(name: String)
case class Shard(id: Short)
case class BuildTier(tier: String)

object Shard {

  // evenly distribute tokens across the range of short (16 bits, from -32768 to +32767 inclusive)
  def fromToken(deviceToken: DeviceToken): Shard = {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(deviceToken.token.getBytes)
    md.reset()

    if (bytes.length > 2) {
      val lastTwoBytes = bytes.slice(bytes.length - 2, bytes.length)
      Shard(ByteBuffer.wrap(lastTwoBytes).getShort)
    } else {
      Shard(0)
    }
  }
}

object BuildTier {

  def chooseTier(buildTier: Option[String], platform: Platform, appVersion: Option[String]): Option[BuildTier] = (buildTier, platform, appVersion) match {
    case (Some("DEBUG"), _, _) => Some(BuildTier("DEBUG"))
    case (Some("BETA"), Android, None) => Some(BuildTier("RELEASE")) //This case is a temporary lie to cope with the Android Firebase migration; it should be removed with https://theguardian.atlassian.net/browse/MSS-1392
    case (Some("BETA"), _, _) => Some(BuildTier("BETA"))
    case (Some("RELEASE"), _, _) => Some(BuildTier("RELEASE"))
    case _ => None
  }

}
