package gu.msnotifications

import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.codec.binary.Base64

object SasTokenGeneration {

  val expiresInMins = 60

  /**
   * From:
   * [[https://github.com/Azure/azure-notificationhubs-java-backend/blob/master/NotificationHubs/src/com/windowsazu0re/messaging/NamespaceManager.java#L233]]
   * Licensed under Apache License 2.0 - [[https://github.com/Azure/azure-notificationhubs-java-backend/blob/master/LICENSE]]
   */
  def generateSasToken(sasKeyName: String, sasKeyValue: String, uri: String): String = {
    val targetUri = URLEncoder.encode(uri.toLowerCase, "UTF-8").toLowerCase
    val expires = (System.currentTimeMillis + (expiresInMins * 60 * 1000)) / 1000
    val rawHmac = {
      val toSign = s"$targetUri\n$expires"
      val keyBytes = sasKeyValue.getBytes("UTF-8")
      val signingKey = new SecretKeySpec(keyBytes, "HmacSHA256")
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(signingKey)
      mac.doFinal(toSign.getBytes("UTF-8"))
    }
    val signature = URLEncoder.encode(Base64.encodeBase64String(rawHmac), "UTF-8")
    s"SharedAccessSignature sr=$targetUri&sig=$signature&se=$expires&skn=$sasKeyName"
  }

}
