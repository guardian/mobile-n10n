package models

import org.joda.time.{DateTimeZone, DateTime}
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

  lazy implicit val jodaFormat = new Format[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] =
      Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ").reads(json).map(_.withZone(DateTimeZone.UTC))

    override def writes(o: DateTime): JsValue =
      Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").writes(o.withZone(DateTimeZone.UTC))
  }

  implicit def optionFormat[T](implicit format: Format[T]): Format[Option[T]] = new Format[Option[T]] {
    override def reads(json: JsValue): JsResult[Option[T]] = json match {
      case JsNull => JsSuccess(None)
      case _ => format.reads(json) map Some.apply
    }

    override def writes(o: Option[T]): JsValue =
      o map format.writes getOrElse JsNull
  }
}
