package binders

import azure.NotificationHubRegistrationId
import models._
import play.api.mvc.{Codec, PathBindable}
import scala.language.implicitConversions

package object pathbinders {

  class Parsing[A](parse: String => Either[String, A], serialize: A => String, typeName: String)(implicit codec: Codec)
    extends PathBindable[A] {

    override def bind(key: String, value: String): Either[String, A] =
      parse(value).left.map(message => s"Cannot parse parameter $key as $typeName" + (if (message.nonEmpty) s": $message" else ""))

    override def unbind(key: String, value: A): String = serialize(value)
  }

  implicit def pathbindableNotificationType: PathBindable[NotificationType] = new Parsing[NotificationType](
    parse = NotificationType.fromRep.get(_).map(Right(_)).getOrElse(Left("")),
    serialize = NotificationType.toRep,
    typeName = "NotificationType"
  )

  implicit def pathbindableNotificationHubRegistrationId: PathBindable[NotificationHubRegistrationId] = new Parsing[NotificationHubRegistrationId](
    parse = NotificationHubRegistrationId.fromString(_).toEither,
    serialize = _.toString,
    typeName = "registration id"
  )

  implicit def pathbindableTopic: play.api.mvc.PathBindable[Topic] = new Parsing[Topic](
    parse = Topic.fromString(_).toEither,
    serialize = _.toString,
    typeName = "topic"
  )

  implicit def pathbindableUniqueDeviceIdentifier: PathBindable[UniqueDeviceIdentifier] = new Parsing[UniqueDeviceIdentifier](
    parse = UniqueDeviceIdentifier.fromString(_).toRight("Invalid udid"),
    serialize = _.toString,
    typeName = "udid"
  )

  implicit def pathbindablePlatform: PathBindable[Platform] = new Parsing[Platform](
    parse = Platform.fromString(_).toRight("Invalid platform"),
    serialize = _.toString,
    typeName = "platform"
  )
}
