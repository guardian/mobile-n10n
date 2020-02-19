package db

import models.Platform

case class HarvestedToken(
  token: String,
  platform: Platform
)
