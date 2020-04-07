package db

import org.specs2.mutable.Specification

class VersionSpec extends Specification {
  "db.BuildTier.versionBefore" should {
    "return true when the client sends no appVersion" in {
      BuildTier.versionBefore(None, 2000) should beTrue
    }
    "return false if the build version is equal or greater" in {
      BuildTier.versionBefore(Some("6.40.2001"), 2000) should beFalse
      BuildTier.versionBefore(Some("6.40.2000"), 2000) should beFalse
    }
    "return true if the build version is lower" in {
      BuildTier.versionBefore(Some("6.40.1999"), 2000) should beTrue
    }
    "return false if the build number can't be parsed" in {
      BuildTier.versionBefore(Some("6.40.2000-beta"), 2000) should beFalse
    }
  }
}
