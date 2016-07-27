package models

import java.net.URI

import play.api.libs.json._

sealed trait Link {
  def webUri: URI
}

object Link {
  case class External(url: String) extends Link {
    def webUri: URI = new URI(url)
  }
  object External {
    implicit val jf = Json.format[External]
  }

  case class Internal(contentApiId: String, shortUrl: Option[String], git: GuardianItemType) extends Link {
    def webUri: URI = new URI(s"http://www.theguardian.com/$contentApiId") // todo: remove hardcoded domain/protocol
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
