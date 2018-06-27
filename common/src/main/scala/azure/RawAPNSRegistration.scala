package azure

import models.Registration

object RawAPNSRegistration {
  def fromMobileRegistration(m: Registration): RawAPNSRegistration = {
    RawAPNSRegistration(
      deviceToken = m.deviceId,
      tags = Tags()
        .withTopics(m.topics)
        .asSet
    )
  }
}
case class RawAPNSRegistration(deviceToken: String, tags: Set[String]) extends NotificationsHubRegistration {

  def toXml: scala.xml.Elem =
    <entry xmlns="http://www.w3.org/2005/Atom">
      <content type="application/xml">
        <AppleRegistrationDescription xmlns:i="http://www.w3.org/2001/XMLSchema-instance"
                                    xmlns="http://schemas.microsoft.com/netservices/2010/10/servicebus/connect">
          {if (tags.nonEmpty) <Tags>{tags.mkString(",")}</Tags> }
          <DeviceToken>{deviceToken}</DeviceToken>
        </AppleRegistrationDescription>
      </content>
    </entry>

}