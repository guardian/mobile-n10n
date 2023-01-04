package models

import java.net.URI

import org.joda.time.{DateTimeZone, DateTime}
import play.api.libs.json._

import scala.util.Try

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
      JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ").reads(json).map(_.withZone(DateTimeZone.UTC))

    override def writes(o: DateTime): JsValue =
      JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").writes(o.withZone(DateTimeZone.UTC))
  }

  implicit def optionFormat[T](implicit format: Format[T]): Format[Option[T]] = new Format[Option[T]] {
    override def reads(json: JsValue): JsResult[Option[T]] = json match {
      case JsNull => JsSuccess(None)
      case _ => format.reads(json) map Some.apply
    }

    override def writes(o: Option[T]): JsValue =
      o map format.writes getOrElse JsNull
  }

  implicit val uriFormat = new Format[URI] {
    override def reads(json: JsValue): JsResult[URI] = json match {
      case JsString(uri) => Try(new URI(uri)).map(u => JsSuccess(u)).getOrElse(JsError(s"Invalid URI: $uri"))
      case _ => JsError("URI must be a String")
    }

    override def writes(o: URI): JsValue = JsString(o.toString)
  }

  def stringFormat[T](fromString: String => Option[T]): Format[T] = new Format[T] {
    override def reads(json: JsValue): JsResult[T] = json.validate[String].flatMap { s =>
      fromString(s)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid string: $s"))
    }

    override def writes(o: T): JsValue = JsString(o.toString)
  }
}
