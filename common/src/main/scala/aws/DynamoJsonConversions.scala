package aws

import java.util.Base64
import scala.collection.JavaConverters._
import play.api.libs.json._
import com.amazonaws.services.dynamodbv2.model.AttributeValue

import java.nio.ByteBuffer

object DynamoJsonConversions {

  def toAttributeMap[T](o: T)(implicit tjs: OWrites[T]): Map[String, AttributeValue] = tjs.writes(o) match {
    case JsObject(m) => m.mapValues(toAttributeValue).toMap
  }

  def fromAttributeMap[T](m: Map[String, AttributeValue])(implicit fjs: Reads[T]): JsResult[T] = {
    fjs.reads(JsObject(m.mapValues(fromAttributeValue)))
  }

  def jsonFromAttributeMap(m: Map[String, AttributeValue]): JsObject = {
    JsObject(m.mapValues(fromAttributeValue))
  }

  private def fromAttributeValue(att: AttributeValue): JsValue = List(
    Option(att.getS) map parseString,
    Option(att.getN) map parseNumber,
    Option(att.isBOOL) map parseBool,
    Option(att.getL) map parseList,
    Option(att.getM) map parseMap,
    Option(att.getB) map parseBinary,
    Option(att.getSS) map parseStringList,
    Option(att.getNS) map parseNumberList,
    Option(att.getBS) map parseBinaryList
  ).flatten.headOption getOrElse JsNull

  private def toAttributeValue(obj: JsValue): AttributeValue = {
    val att = new AttributeValue()
    obj match {
      case JsString(s) => att.setS(s)
      case JsNumber(n) => att.setN(n.toString())
      case JsBoolean(b) => att.setBOOL(b)
      case JsNull => att.setNULL(true)
      case JsArray(a) => att.setL(a.map(toAttributeValue).asJava)
      case JsObject(o) => att.setM(o.mapValues(toAttributeValue).asJava)
    }
    att
  }

  private def parseString(str: String) = JsString(str)

  private def parseNumber(num: String) = JsNumber(BigDecimal(num))

  private def parseBool(bool: java.lang.Boolean) = JsBoolean(bool)

  private def parseList(ls: java.util.List[AttributeValue]) = JsArray(ls.asScala map fromAttributeValue)

  private def parseMap(m: java.util.Map[String, AttributeValue]) = JsObject(m.asScala mapValues fromAttributeValue)

  private def parseBinary(bin: ByteBuffer) = JsString(Base64.getEncoder.encodeToString(bin.array))

  private def parseStringList(ls: java.util.List[String]) = JsArray(ls.asScala map { str => JsString(str) })

  private def parseNumberList(ls: java.util.List[String]) = JsArray(ls.asScala map { num => JsNumber(BigDecimal(num)) })

  private def parseBinaryList(ls: java.util.List[ByteBuffer]) = JsArray(ls.asScala map { bin => JsString(Base64.getEncoder.encodeToString(bin.array)) })

}
