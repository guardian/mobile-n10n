package com.gu.notifications.workerlambda.models

import play.api.libs.json._

sealed case class GuardianItemType(mobileAggregatorPrefix: String)

object GuardianItemType {
  implicit val reads = Json.reads[GuardianItemType].collect(JsonValidationError("Unrecognised item type")) {
    case GuardianItemType("section") => GITSection
    case GuardianItemType("latest") => GITTag
    case GuardianItemType("item-trimmed") => GITContent
  }

  implicit val writes = Json.writes[GuardianItemType]
}

object GITSection extends GuardianItemType("section")
object GITTag extends GuardianItemType("latest")
object GITContent extends GuardianItemType("item-trimmed")