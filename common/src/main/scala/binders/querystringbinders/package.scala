package binders

import models._
import org.joda.time.DateTime
import play.api.mvc.QueryStringBindable

import PartialFunction.condOpt

package object querystringbinders {

  class Parsing[A](parse: String => Either[String, A], serialize: A => String, typeName: String)
    extends QueryStringBindable[A] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, A]] = params.get(key).flatMap(_.headOption).map { p =>
      parse(p).left.map(message => s"Cannot parse parameter $key as $typeName" + (if (message.nonEmpty) s": $message" else ""))
    }

    override def unbind(key: String, value: A): String = key + "=" + serialize(value)
  }

  implicit def qsbindableTopic: QueryStringBindable[Topic] = new Parsing[Topic](
    parse = Topic.fromString(_),
    serialize = _.toString,
    typeName = "Topic"
  )

  implicit def qsbindablePlatform: QueryStringBindable[Platform] = new Parsing[Platform](
    parse = Platform.fromString(_).toRight("Invalid platform"),
    serialize = _.toString,
    typeName = "Platform"
  )

  implicit def qsbindableDateTime: QueryStringBindable[DateTime] = new QueryStringBindable.Parsing[DateTime](
    parse = DateTime.parse,
    serialize = _.toString,
    error = (key: String, e: Exception) => s"Cannot parse parameter $key as DateTimeFloat: ${e.getMessage}"
  )

  implicit def qsbindableNotificationType: QueryStringBindable[NotificationType] = new Parsing[NotificationType](
    parse = NotificationType.fromRep.get(_).map(Right(_)).getOrElse(Left("")),
    serialize = NotificationType.toRep,
    typeName = "NotificationType"
  )
}
