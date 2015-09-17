package gu.msnotifications

import org.scalatest.{Matchers, WordSpec}

class ConnectionStringSpec extends WordSpec with Matchers {
  "Connection string" must {
    "be parsed correctly" in {
      val (namespace, name, keyName, key) = ("a-ns", "b", "keyName", "key/ad=")
      val endpoint = s"""Endpoint=sb://$namespace.servicebus.windows.net/;SharedAccessKeyName=$keyName;SharedAccessKey=$key"""
      val notificationHub = ConnectionSettings.fromString(endpoint).map(_.buildNotificationHub(name)).getOrElse(throw new Exception("error"))
      //notificationHub.namespace shouldBe namespace
      //notificationHub.notificationHub shouldBe name
      notificationHub.secretKeyName shouldBe keyName
      notificationHub.secretKey shouldBe key
    }
  }
}
