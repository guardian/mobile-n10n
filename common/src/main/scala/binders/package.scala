import java.util.UUID

import azure.WNSRegistrationId
import models.Topic
import org.joda.time.DateTime
import play.api.mvc.{PathBindable, QueryStringBindable}

import scala.language.implicitConversions
import scala.util.{Try, Success, Failure}

package object binders {

  implicit def bindRegistrationId(implicit strBindable: PathBindable[String]): PathBindable[WNSRegistrationId] =
    new PathBindable[WNSRegistrationId] {
      override def unbind(key: String, value: WNSRegistrationId): String = {
        strBindable.unbind(
          key = key,
          value = value.registrationId
        )
      }

      override def bind(key: String, value: String): Either[String, WNSRegistrationId] = {
        strBindable.bind(
          key = key,
          value = value
        ).right.flatMap(v => WNSRegistrationId.fromString(v).toEither)
      }
    }

  implicit def bindTopicType(implicit strBindable: PathBindable[String]): PathBindable[Topic] =
    new PathBindable[Topic] {
      override def unbind(key: String, value: Topic): String = {
        strBindable.unbind(
          key = key,
          value = value.toString
        )
      }

      override def bind(key: String, value: String): Either[String, Topic] = {
        strBindable.bind(
          key = key,
          value = value
        ).right.flatMap(v => Topic.fromString(v).toEither)
      }
    }

  implicit def bindDateTime(implicit strBinder: QueryStringBindable[String]) =
    new QueryStringBindable[DateTime] {
      override def unbind(key: String, value: DateTime): String = {
        strBinder.unbind(
          key = key,
          value = value.toString
        )
      }

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String,DateTime]] = {
        strBinder.bind(key, params).map {
          _.right.flatMap { v =>
            Try {
              DateTime.parse(v)
            } match {
              case Success(date) => Right(date)
              case Failure(error) => Left(error.getMessage)
            }
          }
        }
      }
    }

  implicit def bindUUID(implicit strBinder: QueryStringBindable[String]) =
    new QueryStringBindable[UUID] {
      override def unbind(key: String, value: UUID): String =
        strBinder.unbind(key = key, value = value.toString)

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UUID]] =
        strBinder.bind(key, params).map {
          _.right.flatMap { v =>
            Try(UUID.fromString(v)) match {
              case Success(uuid) => Right(uuid)
              case Failure(error) => Left(error.getMessage)
            }
          }
        }
    }
}
