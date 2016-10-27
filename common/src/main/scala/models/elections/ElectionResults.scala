package models.elections

import java.net.URI

import models.JsonUtils
import play.api.libs.json.Json

case class CandidateResults(
  name: String,
  states: List[String],
  electoralVotes: Int,
  popularVotes: Int,
  avatar: Option[URI],
  color: String
)

object CandidateResults {
  import JsonUtils._
  implicit val jf = Json.format[CandidateResults]
}

case class ElectionResults(candidates: List[CandidateResults])

object ElectionResults {
  implicit val jf = Json.format[ElectionResults]
}
