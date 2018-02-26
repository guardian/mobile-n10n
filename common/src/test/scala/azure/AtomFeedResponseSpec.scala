package azure

import NotificationHubClient.HubResult
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import cats.syntax.either._

import scala.xml.Elem

class AtomFeedResponseSpec extends Specification {

  "The AtomEntry parser" should {
    "parse a valid XML into an entry" in new AtomEntryScope {
      val parsedObject = atomReader.reads(xmlEntry("a value")).map(_.content)
      parsedObject shouldEqual Right(SomeObject("a value"))
    }
  }

  "The AtomFeed parser" should {
    "parse a valid XML into a feed" in new AtomFeedScope {
      val entries = List(xmlEntry("Hello"), xmlEntry("this"), xmlEntry("is"), xmlEntry("Doge"))
      val parsedObject = feedReader.reads(xmlFeed(entries)).map(_.items)
      parsedObject shouldEqual Right(List(SomeObject("Hello"), SomeObject("this"), SomeObject("is"), SomeObject("Doge")))
    }
  }

  trait AtomEntryScope extends Scope {
    case class SomeObject(someValue: String)

    implicit val someObjectReader = new XmlReads[SomeObject] {
      override def reads(xml: Elem): HubResult[SomeObject] = Right(SomeObject(xml \ "someValue" text))
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


