package db

import java.sql.Date
import doobie.util.meta.Meta

sealed trait Platform
object Platform {
  case object Android extends Platform { override def toString = "android" }
  case object Apple extends Platform { override def toString = "apple"}

  def unsafeFromString(s: String): Platform = s match {
    case "android" => Android
    case "apple" => Apple
  }
}

object Registration {
  implicit val PlatformMeta: Meta[Platform] =
    Meta[String].xmap(Platform.unsafeFromString, _.toString)
}

case class Registration(device: Device, topic: Topic, shard: Shard, lastModified: Date)
case class Device(token: String, platform: Platform)
case class Topic(name: String)
case class Shard(id: Short)
