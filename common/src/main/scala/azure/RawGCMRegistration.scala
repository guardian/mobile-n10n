package azure

import models.Registration

object RawGCMRegistration {
  def fromMobileRegistration(m: Registration): RawGCMRegistration = {
    RawGCMRegistration(
      gcmRegistrationId = m.deviceToken,
      tags = Tags()
        .withTopics(m.topics)
        .asSet
    )
  }
}
case class RawGCMRegistration(gcmRegistrationId: String, tags: Set[String]) extends NotificationsHubRegistration {

  def toXml: scala.xml.Elem =
    <entry xmlns="http://www.w3.org/2005/Atom">
      <content type="application/xml">
        <GcmRegistrationDescription xmlns:i="http://www.w3.org/2001/XMLSchema-instance"
                                    xmlns="http://schemas.microsoft.com/netservices/2010/10/servicebus/connect">
          {if (tags.nonEmpty) <Tags>{tags.mkString(",")}</Tags> }
          <GcmRegistrationId>{gcmRegistrationId}</GcmRegistrationId>
        </GcmRegistrationDescription>
      </content>
    </entry>

}