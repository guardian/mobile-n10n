package notificationhubs;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;

public class SasTokenGenerator {
    /**
     * From [[https://github.com/Azure/azure-notificationhubs-java-backend/blob/master/NotificationHubs/src/com/windowsazure/messaging/NamespaceManager.java#L233]]
     * Licensed under Apache License 2.0 - [[https://github.com/Azure/azure-notificationhubs-java-backend/blob/master/LICENSE]]
     */
    public static String generateSasToken(String sasKeyName, String sasKeyValue, URI uri) {
        String targetUri;
        try {
            targetUri = URLEncoder
                    .encode(uri.toString().toLowerCase(), "UTF-8")
                    .toLowerCase();

            long expiresOnDate = System.currentTimeMillis();
            int expiresInMins = 60; // 1 hour
            expiresOnDate += expiresInMins * 60 * 1000;
            long expires = expiresOnDate / 1000;
            String toSign = targetUri + "\n" + expires;

            // Get an hmac_sha1 key from the raw key bytes
            byte[] keyBytes = sasKeyValue.getBytes("UTF-8");
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");

            // Get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);

            // Compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(toSign.getBytes("UTF-8"));

            // Convert raw bytes to Hex
            String signature = URLEncoder.encode(
                    Base64.encodeBase64String(rawHmac), "UTF-8");

            // construct authorization string
            String token = "SharedAccessSignature sr=" + targetUri + "&sig="
                    + signature + "&se=" + expires + "&skn=" + sasKeyName;
            return token;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}