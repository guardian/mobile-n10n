package models

import play.api.libs.json._

object JsonUtils {
  implicit def eitherFormat[L, R](implicit leftFormat: Format[L], rightFormat: Format[R]): Format[Either[L, R]] = new Format[Either[L, R]] {
    def reads(json: JsValue): JsResult[Either[L, R]] = {
      rightFormat.reads(json) map { Right(_) }
    } orElse {
      leftFormat.reads(json) map { Left(_) }
    }

    def writes(c: Either[L, R]): JsValue = c match {
      case Left(a) => leftFormat.writes(a)
      case Right(b) => rightFormat.writes(b)
    }
  }
}
