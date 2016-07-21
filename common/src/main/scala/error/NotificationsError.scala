package error

trait NotificationsError {
  def reason: String 
}

trait RequestError extends NotificationsError