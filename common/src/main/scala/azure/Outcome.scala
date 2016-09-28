package azure

import play.api.libs.json.Json

import scala.xml.Elem

case class Outcome(name: OutcomeName, count: Int)

object Outcome {
  import Responses._

  implicit val jf = Json.format[Outcome]

  implicit val reader = new XmlReads[Outcome] {
    def reads(xml: Elem) = {
      for {
        name <- xml.textNode("Name").flatMap(OutcomeName.fromString)
        count <- xml.integerNode("Count")
      } yield Outcome(name, count)
    }
  }
}