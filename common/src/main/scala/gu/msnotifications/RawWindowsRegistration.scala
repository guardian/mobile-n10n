package gu.msnotifications

import models.Registration
/**
 * [[https://msdn.microsoft.com/en-us/library/microsoft.servicebus.notifications.windowsregistrationdescription.aspx]]
 */
object RawWindowsRegistration {
  def fromMobileRegistration(m: Registration): RawWindowsRegistration = {
    RawWindowsRegistration(
      channelUri = m.deviceId,
      tags = Tags()
        .withUserId(m.userId)
        .withTopics(m.topics)
        .asSet
    )
  }
}
case class RawWindowsRegistration(channelUri: String, tags: Set[String]) {

  def toXml: scala.xml.Elem =
    <entry xmlns="http://www.w3.org/2005/Atom">
      <content type="application/xml">
        <WindowsRegistrationDescription xmlns:i="http://www.w3.org/2001/XMLSchema-instance"
                                        xmlns="http://schemas.microsoft.com/netservices/2010/10/servicebus/connect">
          {if (tags.nonEmpty) <Tags>{tags.mkString(", ")}</Tags> }
          <ChannelUri>{channelUri}</ChannelUri>
        </WindowsRegistrationDescription>
      </content>
    </entry>

}

