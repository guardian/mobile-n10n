package db

import db.BuildTier.BuildTier
import models.Platform

case class HarvestedToken(
  token: String,
  platform: Platform,
  buildTier: Option[BuildTier]
)
