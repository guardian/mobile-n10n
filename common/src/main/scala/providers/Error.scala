package providers

trait Error {
  def providerName: String
  def reason: String
}