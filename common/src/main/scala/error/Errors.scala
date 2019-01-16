package error

case class UnsupportedPlatform(platform: String) extends RequestError {
  override def reason: String = s"Platform '$platform' is not supported"
}

case class MalformattedRegistration(description: String) extends RequestError {
  override def reason: String = s"Malformatred request: $description"
}