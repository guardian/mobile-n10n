import gu.msnotifications.WNSRegistrationId
import play.api.mvc.PathBindable

import scala.language.implicitConversions

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

}
