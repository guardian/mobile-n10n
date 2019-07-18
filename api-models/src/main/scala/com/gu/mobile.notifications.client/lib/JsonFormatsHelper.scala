package com.gu.mobile.notifications.client.lib

import java.net.URI

import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

import scala.util.Try

object JsonFormatsHelper {
  implicit class RichJsObject(obj: JsObject) {
    def +:(kv: (String, JsValue)): JsObject = obj match {
      case JsObject(entries) => JsObject(entries + kv)
    }
    def ++:(kv: Map[String, JsValue]): JsObject = obj match {
      case JsObject(entries) => JsObject(kv ++: entries)
    }
  }

  implicit class RichWrites[A](writes: Writes[A]) {
    def withTypeString(typ: String): Writes[A] = writes transform { _ match {
      case obj: JsObject => ("type" -> JsString(typ)) +: obj
      case x: JsValue => x
    }}

    def withAdditionalFields(fields: Map[String, JsValue]): Writes[A] = writes transform { _ match {
      case obj: JsObject => fields ++: obj
      case x: JsValue => x
    }}

    def withAdditionalStringFields(fields: Map[String, String]): Writes[A] = withAdditionalFields(fields.mapValues(JsString))
  }


  implicit val urlFormat = new Format[URI] {
    override def writes(o: URI): JsValue = JsString(o.toString)
    override def reads(json: JsValue): JsResult[URI] = json match {
      case JsString(uri) => Try(JsSuccess(new URI(uri))).getOrElse(JsError(s"Invalid uri: $uri"))
      case _ => JsError("Invalid url type")
    }
  }
}