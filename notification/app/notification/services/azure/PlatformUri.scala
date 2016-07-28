package notification.services.azure

case class PlatformUri(uri: String, `type`: PlatformUriType)

sealed trait PlatformUriType

object PlatformUriTypes {

  case object Item extends PlatformUriType {
    override def toString: String = "item"
  }

  case object FootballMatch extends PlatformUriType {
    override def toString: String = "football-match"
  }

  case object External extends PlatformUriType {
    override def toString: String = "external"
  }

}