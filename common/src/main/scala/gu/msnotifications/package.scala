package gu

import java.util.Base64
import scala.util.Try

package object msnotifications {

  private def encode(string: String): String =
    Base64.getUrlEncoder.encodeToString(string.getBytes("UTF-8"))

  private def decode(string: String): String =
    new String(Base64.getUrlDecoder.decode(string), "UTF-8")

  case class Topic(`type`: String, name: String) {
    def toUri: String = s"topic:${encode(`type`)}:${encode(name)}"
  }

  object Topic {
    def fromUri(uri: String): Option[Topic] = {
      val regex = s"""topic:(.*):(.*)""".r
      PartialFunction.condOpt(uri) {
        case regex(tpe, name) =>
          Topic(`type` = decode(tpe), name = decode(name))
      }
    }
  }
}