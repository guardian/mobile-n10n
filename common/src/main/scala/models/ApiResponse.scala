package models

case class ApiResponse(result: String)

object ApiResponse {
  import play.api.libs.json._

  implicit val jf = Json.format[ApiResponse]
}
