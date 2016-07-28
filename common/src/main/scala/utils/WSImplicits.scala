package utils

import play.api.libs.ws.WSResponse

object WSImplicits {
  implicit class RichWSResponse(val response: WSResponse) extends AnyVal {
    def isSuccess: Boolean = response.status >= 200 && response.status < 300
  }
}
