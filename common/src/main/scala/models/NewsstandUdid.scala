package models

import java.util.UUID

object NewsstandUdid {
  def fromDeviceToken(pushToken: String): NewsstandUdid = new NewsstandUdid(pushToken)
}

class NewsstandUdid(val pushToken: String)
  extends UniqueDeviceIdentifier(UUID.nameUUIDFromBytes(pushToken.getBytes)) {

  override def legacyFormat: String = "newsstand:" + play.api.libs.Codecs.sha1(s"ns-$pushToken")
}