package gu

import java.util.Base64
import scala.util.Try

package object msnotifications {

  private object Decoded {
    def unapply(str: String): Option[String] =
      Try(decode(str)).toOption
  }

  private def encode(string: String): String =
    Base64.getUrlEncoder.encodeToString(string.getBytes("UTF-8"))

  private def decode(string: String): String =
    new String(Base64.getUrlDecoder.decode(string), "UTF-8")

  private val alnum = s"""[0-9a-z]+""".r
  private val urigood = s"""[0-9a-zA-Z_/-]+""".r

  case class Topic(`type`: String, name: String) {
    def toUri: String = this match {
      case Topic(alnum(), urigood()) =>
        s"topic:${`type`}:$name"
      case _ =>
        s"topic:${encode(`type`)}:${encode(name)}"
    }
  }

  object Topic {
    def fromUri(uri: String): Option[Topic] = {
      val regex = s"""topic:(.*):(.*)""".r
      PartialFunction.condOpt(uri) {
        case regex(Decoded(tpe), Decoded(name)) =>
          Topic(`type` = tpe, name = name)
        case regex(tpe, name) =>
          Topic(`type` = tpe, name = name)
      }
    }
  }


}