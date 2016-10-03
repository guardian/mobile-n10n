package models.pagination

import scala.PartialFunction._

sealed trait Cursor {
  def encoded: String
}

object Cursor {
  import play.api.libs.json._

  implicit val jf = new Format[Cursor] {
    override def reads(json: JsValue): JsResult[Cursor] = json.validate[String].map { s =>
      ProviderCursor.fromString(s)
        .getOrElse(CursorSet.fromString(s))
    }

    override def writes(o: Cursor): JsValue = JsString(o.encoded)
  }
}

object ProviderCursor {
  def fromString(s: String): Option[ProviderCursor] = condOpt(s.split(":").toList) {
    case k :: v :: Nil => ProviderCursor(k, base64Decode(v))
  }

  private def base64Decode(s: String) = new String(java.util.Base64.getDecoder.decode(s.getBytes))
}

case class ProviderCursor(providerId: String, cursor: String) extends Cursor {
  def encoded: String = s"$providerId:${base64Encode(cursor)}"

  private def base64Encode(s: String) = new String(java.util.Base64.getEncoder.encode(s.getBytes))
}

object CursorSet {
  def fromString(s: String): CursorSet = {
    CursorSet(s.split('|').toList.flatMap(ProviderCursor.fromString))
  }
}

case class CursorSet(cursors: List[Cursor]) extends Cursor {
  def encoded: String = cursors.map(_.encoded).mkString("|")

  def providerCursor(providerId: String): Option[ProviderCursor] = cursors.collectFirst {
    case c: ProviderCursor if c.providerId == providerId => c
  }
}