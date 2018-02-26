package utils

import azure.apns.Body
import play.api.http.Writeable
import play.api.libs.ws.BodyWritable

object WriteableImplicits {
  implicit class RichWriteable[T](val w: Writeable[T]) extends AnyVal {
    def withContentType(contentType: String): Writeable[T] = {
      new Writeable[T](w.transform, Some(contentType))
    }
  }
}

