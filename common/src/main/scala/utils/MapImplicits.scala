package utils

object MapImplicits {
  implicit class RichOptionMap[A, B](base: Map[A, Option[B]]) {
    def flattenValues: Map[A, B] = base collect {
      case (k, Some(v)) => k -> v
    }
  }
}
