package models

import java.util.UUID

import scala.util.Try

object IosUdid {
  def fromString(s: String): Option[IosUdid] = {
    if (s.startsWith("gia:"))
      uuidFromString(s.stripPrefix("gia:")).map(id => new IosUdid(s, id))
    else
      None
  }

  private def uuidFromString(s: String) = Try(UUID.fromString(s)).toOption
}

class IosUdid(underlying: String, id: UUID) extends UniqueDeviceIdentifier(id) {

  override def legacyFormat: String = underlying
}