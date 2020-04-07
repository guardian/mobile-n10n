package db

import org.specs2.mutable.Specification

class VersionSpec extends Specification {
  "db.BuildTier.versionAboveOrEqual" should {
    "return false when the client sends no appVersion" in {
      BuildTier.versionAboveOrEqual(None, 2000) should beFalse
    }
    "return false if the build version is bellow" in {
      BuildTier.versionAboveOrEqual(Some("6.40.1999"), 2000) should beFalse
    }
    "return true if the build version is equal or above" in {
      BuildTier.versionAboveOrEqual(Some("6.40.2000"), 2000) should beTrue
      BuildTier.versionAboveOrEqual(Some("6.40.2001"), 2000) should beTrue
    }
    "return false if the build number can't be parsed" in {
      BuildTier.versionAboveOrEqual(Some("6.40.2000-beta"), 2000) should beFalse
    }
  }
}
