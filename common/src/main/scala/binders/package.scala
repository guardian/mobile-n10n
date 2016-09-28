import java.util.UUID

import azure.NotificationHubRegistrationId
import models._

import models.pagination.CursorSet
import org.joda.time.DateTime
import play.api.mvc.{PathBindable, QueryStringBindable}

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

package object binders {

  implicit def bindRegistrationId(implicit strBindable: PathBindable[String]): PathBindable[NotificationHubRegistrationId] =
    new PathBindable[NotificationHubRegistrationId] {
      override def unbind(key: String, value: NotificationHubRegistrationId): String = {
        strBindable.unbind(
          key = key,
          value = value.registrationId
        )
      }

      override def bind(key: String, value: String): Either[String, NotificationHubRegistrationId] = {
        strBindable.bind(
          key = key,
          value = value
        ).right.flatMap(v => NotificationHubRegistrationId.fromString(v).toEither)
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

  implicit def bindUdid(implicit strBindable: PathBindable[String]): PathBindable[UniqueDeviceIdentifier] =
    new PathBindable[UniqueDeviceIdentifier] {
      override def unbind(key: String, value: UniqueDeviceIdentifier): String =
        strBindable.unbind(key = key, value = value.toString)

      override def bind(key: String, value: String): Either[String, UniqueDeviceIdentifier] =
        strBindable.bind(key, value).right.flatMap(v => UniqueDeviceIdentifier.fromString(v).toRight("Invalid udid"))
    }

  implicit def bindPlatform(implicit strBindable: PathBindable[String]): PathBindable[Platform] =
    new PathBindable[Platform] {
      override def unbind(key: String, value: Platform): String =
        strBindable.unbind(key = key, value = value.toString)

      override def bind(key: String, value: String): Either[String, Platform] =
        strBindable.bind(key, value).right.flatMap(v => Platform.fromString(v).toRight("Invalid platform"))
    }

  implicit def bindDateTime(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[DateTime] =
    new QueryStringBindable[DateTime] {
      override def unbind(key: String, value: DateTime): String = {
        strBinder.unbind(
          key = key,
          value = value.toString
        )
      }

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, DateTime]] = {
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

  implicit def bindUUID(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[UUID] =
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

  implicit def bindCursorSet(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[CursorSet] =
    new QueryStringBindable[CursorSet] {
      override def unbind(key: String, value: CursorSet): String =
        strBinder.unbind(key = key, value = value.encoded)

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, CursorSet]] =
        strBinder.bind(key, params).map {
          _.right.map(CursorSet.fromString)
        }
    }

  implicit def bindNotificationType(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[NotificationType] =
    new QueryStringBindable[NotificationType] {
      override def unbind(key: String, value: NotificationType): String =
        strBinder.unbind(key = key, value = value.toString)

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, NotificationType]] =
        strBinder.bind(key, params).map {
          _.right.flatMap { v =>
            NotificationType.fromRep.get(v) match {
              case Some(notificationType) => Right(notificationType)
              case None => Left(s"Unknown NotificationType $v")
            }
          }
        }
    }

  implicit def bindNotificationType(implicit strBinder: PathBindable[String]): PathBindable[NotificationType] =
    new PathBindable[NotificationType] {
      override def unbind(key: String, value: NotificationType): String = {
        strBinder.unbind(
          key = key,
          value = NotificationType.toRep(value)
        )
      }

      override def bind(key: String, value: String): Either[String, NotificationType] = {
        strBinder.bind(
          key = key,
          value = value
        ).right.flatMap { v => NotificationType.fromRep.get(v) match {
            case Some(notificationType) => Right(notificationType)
            case None => Left(s"Unknown NotificationType $v")
          }
        }
      }
    }
}
