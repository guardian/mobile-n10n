package azure

case class AzureRawPush(wnsType: String = "wns/raw", body: String, tags: Option[Set[Tag]]) {
  def tagQuery: Option[String] = tags.map { set =>
    set.map(_.encodedTag).mkString("(", " && ", ")")
  }
}