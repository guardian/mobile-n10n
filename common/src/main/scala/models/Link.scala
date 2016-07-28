package models

import play.api.libs.json._

sealed trait Link
object Link {
  case class External(url: String) extends Link
  object External {
    implicit val jf = Json.format[External]
  }

  case class Internal(contentApiId: String, git: GuardianItemType) extends Link
  object Internal {
    implicit val jf = Json.format[Internal]
  }

  implicit val jf = new Format[Link] {
    override def writes(o: Link): JsValue = o match {
      case external: External => External.jf.writes(external)
      case internal: Internal => Internal.jf.writes(internal)
    }

    override def reads(json: JsValue): JsResult[Link] = {
      Internal.jf.reads(json) orElse External.jf.reads(json)
    }
  }
}
