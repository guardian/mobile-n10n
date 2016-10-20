package models

import java.net.URI

import play.api.libs.json._

sealed trait Link {
  def webUri(frontendBaseUrl: String): URI
  def text: Option[String]
}

object Link {
  case class External(url: String, text: Option[String] = None) extends Link {
    def webUri(frontendBaseUrl: String): URI = new URI(url)
  }
  object External {
    implicit val jf = Json.format[External]
  }

  case class Internal(contentApiId: String, shortUrl: Option[String], git: GuardianItemType, text: Option[String] = None) extends Link {
    def webUri(frontendBaseUrl: String): URI = new URI(s"$frontendBaseUrl$contentApiId")
  }
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
