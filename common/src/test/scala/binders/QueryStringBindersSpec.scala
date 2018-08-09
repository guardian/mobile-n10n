package binders

import org.specs2.mutable.Specification
import binders.querystringbinders._
import models.{Android, AzureToken, BothTokens, FcmToken}

class QueryStringBindersSpec extends Specification {

  "A qsbindableRegistrationsByDeviceToken" should {
    "fail to bind if no parameters are provided" in {
      val result = qsbindableRegistrationsByDeviceToken.bind("", Map.empty)
      result should beSome.which(_ should beLeft)
    }
    "fail to bind if only platform is provided" in {
      val result = qsbindableRegistrationsByDeviceToken.bind("", Map("platform" -> List("android")))
      result should beSome.which(_ should beLeft)
    }
    "bind if platform and azure token are provided" in {
      val result = qsbindableRegistrationsByDeviceToken.bind("", Map(
        "platform" -> List("android"),
        "azureToken" -> List("abc")
      ))
      result should beSome.which(_ should beRight(RegistrationsByDeviceToken(Android, AzureToken("abc"))))
    }
    "bind if platform and firebase token are provided" in {
      val result = qsbindableRegistrationsByDeviceToken.bind("", Map(
        "platform" -> List("android"),
        "firebaseToken" -> List("abc")
      ))
      result should beSome.which(_ should beRight(RegistrationsByDeviceToken(Android, FcmToken("abc"))))
    }
    "bind if platform and both firebase and azure token are provided" in {
      val result = qsbindableRegistrationsByDeviceToken.bind("", Map(
        "platform" -> List("android"),
        "firebaseToken" -> List("abc"),
        "azureToken" -> List("def")
      ))
      val expected = BothTokens("def", "abc")
      result should beSome.which(_ should beRight(RegistrationsByDeviceToken(Android, expected)))
    }
  }

}
