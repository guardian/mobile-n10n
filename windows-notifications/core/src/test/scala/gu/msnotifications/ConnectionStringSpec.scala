package gu.msnotifications

import org.scalatest.{Matchers, WordSpec}

class ConnectionStringSpec extends WordSpec with Matchers {
  "Connection string" must {
    "be parsed" in {
      val (a, keyName, key) = ("a", "keyName", "key")
      val endpoint = s"""Endpoint=sb://$a-ns.servicebus.windows.net/;SharedAccessKeyName=$keyName;SharedAccessKey=$key"""
      val notificationHub = ConnectionString(endpoint).buildNotificationHub.get
      notificationHub.namespace shouldBe s"$a-ns"
      notificationHub.notificationHub shouldBe a
      notificationHub.secretKeyName shouldBe keyName
      notificationHub.secretKey shouldBe key
    }
  }
}
