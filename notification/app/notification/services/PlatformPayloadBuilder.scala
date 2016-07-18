package notification.services

import java.net.URI

import models.Link

trait PlatformPayloadBuilder {

  sealed trait PlatformUriType

  case object Item extends PlatformUriType {
    override def toString: String = "item"
  }

  case object FootballMatch extends PlatformUriType {
    override def toString: String = "football-match"
  }

  case object External extends PlatformUriType {
    override def toString: String = "external"
  }

  case class PlatformUri(uri: String, `type`: PlatformUriType)

  protected def replaceHost(uri: URI) = List(Some("x-gu://"), Option(uri.getPath), Option(uri.getQuery).map("?" + _)).flatten.mkString

  protected def toPlatformLink(link: Link) = link match {
    case Link.Internal(contentApiId) => PlatformUri(s"x-gu:///items/$contentApiId", Item)
    case Link.External(url) => PlatformUri(url, External)
  }

  protected def mapWithOptionalValues(elems: (String, String)*)(optionals: (String, Option[String])*) =
    elems.toMap ++ optionals.collect { case (k, Some(v)) => k -> v }
}