package azure

import NotificationHubClient.HubResult
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scalaz.syntax.either._

import scala.xml.Elem

class AtomFeedResponseSpec extends Specification {

  "The AtomEntry parser" should {
    "parse a valid XML into an entry" in new AtomEntryScope {
      val parsedObject = atomReader.reads(xmlEntry("a value")).map(_.content)
      parsedObject shouldEqual SomeObject("a value").right
    }
  }

  "The AtomFeed parser" should {
    "parse a valid XML into a feed" in new AtomFeedScope {
      val entries = List(xmlEntry("Hello"), xmlEntry("this"), xmlEntry("is"), xmlEntry("Doge"))
      val parsedObject = feedReader.reads(xmlFeed(entries)).map(_.items)
      parsedObject shouldEqual List(SomeObject("Hello"), SomeObject("this"), SomeObject("is"), SomeObject("Doge")).right
    }
  }

  trait AtomEntryScope extends Scope {
    case class SomeObject(someValue: String)

    implicit val someObjectReader = new XmlReads[SomeObject] {
      override def reads(xml: Elem): HubResult[SomeObject] = SomeObject(xml \ "someValue" text).right
    }

    implicit val atomReader = AtomEntry.reader[SomeObject]

    def xmlEntry(value: String) = <entry a:etag="W/&quot;1&quot; " xmlns:a="http://schemas.microsoft.com/ado/2007/08/dataservices/metadata">
      <title type="text">some title</title>
      <content type="application/xml">
        <someObject>
          <someValue>{value}</someValue>
        </someObject>
      </content>
    </entry>
  }

  trait AtomFeedScope extends Scope with AtomEntryScope {

    implicit val feedReader = AtomFeedResponse.reader[SomeObject]

    def xmlFeed(entries: List[Elem]) = <feed xmlns="http://www.w3.org/2005/Atom">
      <title type="text">Registrations</title>
      {entries}
    </feed>
  }

}


