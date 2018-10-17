package db

import java.sql.Timestamp
import java.time.{LocalDateTime, ZoneOffset}

import doobie.util.meta.Meta
import models.Platform

object Registration {
  implicit val PlatformMeta: Meta[Platform] =
    Meta[String].xmap(
      s => Platform.fromString(s).getOrElse(throw doobie.util.invariant.InvalidEnum[Platform](s)),
      _.toString
    )

  implicit val DateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].xmap(
      ts => ts.toLocalDateTime,
      dt => Timestamp.valueOf(dt)
    )
}

case class Registration(device: Device, topic: Topic, shard: Shard, lastModified: Option[LocalDateTime] = None)
case class Device(token: String, platform: Platform)
case class Topic(name: String)
case class Shard(id: Short)
