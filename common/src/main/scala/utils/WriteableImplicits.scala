package utils

import play.api.http.Writeable

object WriteableImplicits {
  implicit class RichWriteable[T](val w: Writeable[T]) extends AnyVal {
    def withContentType(contentType: String): Writeable[T] = {
      new Writeable[T](w.transform, Some(contentType))
    }
  }
}
