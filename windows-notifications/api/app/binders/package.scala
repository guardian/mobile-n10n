import models.RegistrationId
import play.api.mvc.PathBindable

import scala.language.implicitConversions

package object binders {

  implicit def bindRegistrationId(implicit strBindable: PathBindable[String]): PathBindable[RegistrationId] =
    new PathBindable[RegistrationId] {
      override def unbind(key: String, value: RegistrationId): String = {
        strBindable.unbind(
          key = key,
          value = value.registrationId
        )
      }

      override def bind(key: String, value: String): Either[String, RegistrationId] = {
        strBindable.bind(
          key = key,
          value = value
        ).right.flatMap(v => RegistrationId.fromString(v))
      }
    }

}
