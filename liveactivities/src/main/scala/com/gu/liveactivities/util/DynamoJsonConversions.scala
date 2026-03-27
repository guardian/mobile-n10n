package com.gu.liveactivities.util

import java.util.Base64
import scala.jdk.CollectionConverters._
import play.api.libs.json._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.core.BytesWrapper

import java.nio.ByteBuffer
import software.amazon.awssdk.core.SdkBytes

object DynamoJsonConversions {

  def toAttributeMap[T](o: T)(implicit tjs: OWrites[T]): Map[String, AttributeValue] = tjs.writes(o) match {
    case JsObject(m) => m.view.mapValues(toAttributeValue).toMap
  }

  def fromAttributeMap[T](m: Map[String, AttributeValue])(implicit fjs: Reads[T]): JsResult[T] = {
    fjs.reads(JsObject(m.view.mapValues(fromAttributeValue).toMap))
  }

  def jsonFromAttributeMap(m: Map[String, AttributeValue]): JsObject = {
    JsObject(m.view.mapValues(fromAttributeValue).toMap)
  }

  private def fromAttributeValue(att: AttributeValue): JsValue = 
    List(
      Option(att.s()) map parseString,
      Option(att.n()) map parseNumber,
      Option(att.bool()) map parseBool,
      if (att.hasL()) Some(parseList(att.l())) else None,
      if (att.hasM()) Some(parseMap(att.m())) else None,
      Option(att.b()) map parseBinary,
      if (att.hasSs()) Some(parseStringList(att.ss())) else None,
      if (att.hasNs()) Some(parseNumberList(att.ns())) else None,
      if (att.hasBs()) Some(parseBinaryList(att.bs())) else None
  ).flatten.headOption getOrElse JsNull

  private def toAttributeValue(obj: JsValue): AttributeValue = {
    val att = AttributeValue.builder()
    obj match {
      case JsString(s) => att.s(s)
      case JsNumber(n) => att.n(n.toString())
      case JsBoolean(b) => att.bool(b)
      case JsNull => att.nul(true)
      case JsArray(a) => att.l(a.map(toAttributeValue).asJava)
      case JsObject(o) => att.m(o.view.mapValues(toAttributeValue).toMap.asJava)
      case JsFalse => att.bool(false)
      case JsTrue => att.bool(true)
    }
    att.build()
  }

  private def parseString(str: String) = JsString(str)

  private def parseNumber(num: String) = JsNumber(BigDecimal(num))

  private def parseBool(bool: java.lang.Boolean) = JsBoolean(bool)

  private def parseList(ls: java.util.List[AttributeValue]) = JsArray(ls.asScala map fromAttributeValue)

  private def parseMap(m: java.util.Map[String, AttributeValue]) = JsObject(m.asScala.view.mapValues(fromAttributeValue).toMap)

  private def parseBinary(bin: SdkBytes) = JsString(Base64.getEncoder.encodeToString(bin.asByteArray))

  private def parseStringList(ls: java.util.List[String]) = JsArray(ls.asScala map { str => JsString(str) })

  private def parseNumberList(ls: java.util.List[String]) = JsArray(ls.asScala map { num => JsNumber(BigDecimal(num)) })

  private def parseBinaryList(ls: java.util.List[SdkBytes]) = JsArray(ls.asScala map { bin => JsString(Base64.getEncoder.encodeToString(bin.asByteArray())) })

}
