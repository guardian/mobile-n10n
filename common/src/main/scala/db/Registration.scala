package db

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDateTime

import db.BuildTier.BuildTier
import doobie.util.meta.Meta

import models.{Android, DeviceToken, Platform}

import scala.util.Try

object Registration {
  implicit val PlatformMeta: Meta[Platform] =
    Meta[String].timap(
      s => Platform.fromString(s).getOrElse(throw doobie.util.invariant.InvalidEnum[Platform](s)),
    )(_.toString)

  implicit val BuildTierMeta: Meta[BuildTier] =
    Meta[String].timap(
      s => BuildTier.fromString(s).getOrElse(throw doobie.util.invariant.InvalidEnum[BuildTier](s)),
    )(_.toString)
}

case class Registration(device: Device, topic: Topic, shard: Shard, lastModified: Option[LocalDateTime] = None, buildTier: Option[BuildTier])
case class Device(token: String, platform: Platform)
case class Topic(name: String)
case class Shard(id: Short)

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

object BuildTier extends Enumeration {

  type BuildTier = Value
  val DEBUG, BETA, RELEASE = Value

  def fromString(s: String): Option[BuildTier] = values.find(_.toString == s)

  def versionBefore(appVersion: Option[String], targetBuild: Int): Boolean = appVersion match {
    case None => true
    case Some(versionString) =>
      versionString
        .split("\\.")
        .lastOption
        .flatMap(build => Try(build.toInt).toOption)
        .exists( _ < targetBuild)
  }

  def chooseTier(buildTier: Option[String], platform: Platform, appVersion: Option[String]): Option[BuildTier] = {
    buildTier.flatMap { tier =>
      val tierFromClient = BuildTier.fromString(tier)
      if (tierFromClient.contains(BETA) && platform == Android && versionBefore(appVersion, 2274)) { //This case is a temporary lie to cope with the Android Firebase migration; it should be removed with https://theguardian.atlassian.net/browse/MSS-1392
        Some(RELEASE)
      } else {
        tierFromClient
      }
    }
  }

}
