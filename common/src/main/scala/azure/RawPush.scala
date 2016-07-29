package azure

import play.api.http.Writeable

trait RawPush[T] {

  def body: T

  def tags: Option[Tags]

  def format: String

  def writeable: Writeable[T]

  def tagQuery: Option[String] = tags.map { set =>
    set.tags.map(_.encodedTag).mkString("(", " || ", ")")
  }

  def extraHeaders: List[(String, String)] = Nil
}