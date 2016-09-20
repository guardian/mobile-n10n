package models

import java.util.UUID

object NewsstandUdid {
  def fromDeviceToken(pushToken: String): NewsstandUdid = new NewsstandUdid(pushToken)
}

case class NewsstandUdid(pushToken: String) extends UniqueDeviceIdentifier {

  override val id = UUID.nameUUIDFromBytes(pushToken.getBytes)

  override def legacyFormat: String = "newsstand:" + play.api.libs.Codecs.sha1(s"ns-$pushToken")
}