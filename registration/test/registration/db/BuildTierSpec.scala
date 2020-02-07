package registration.db

import db.BuildTier
import models.{Android, Ios}
import org.specs2.mutable.Specification

class BuildTierSpec extends Specification {

  "chooseTier" should {

    // This test should be deleted as part of https://theguardian.atlassian.net/browse/MSS-1392
    "Mark an Android beta build which fails to send an app version as a release build" in {
      val result = BuildTier.chooseTier(Some("BETA"), Android, None)
      result.contains(BuildTier("RELEASE"))
    }

    "Mark an Android beta build which includes an app version as a beta build" in {
      val result = BuildTier.chooseTier(Some("BETA"), Android, Some("3000"))
      result.contains(BuildTier("BETA"))
    }

    "Mark an iOS beta build as a beta build" in {
      val result = BuildTier.chooseTier(Some("BETA"), Ios, None)
      result.contains(BuildTier("BETA"))
    }

    "Return None if the client sends an invalid build tier" in {
      val result = BuildTier.chooseTier(Some("NOT_A_TIER"), Ios, None)
      result.isEmpty
    }

  }

}
