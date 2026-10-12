package com.gu.liveactivities.service

import java.time.Instant
import java.util.concurrent.atomic._
import com.eatthepath.pushy.apns.auth.ApnsSigningKey
import com.eatthepath.pushy.apns.auth.AuthenticationToken
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import com.gu.liveactivities.util.Logging

class Authentication(teamId: String, keyId: String, certificate: String) extends Logging {

  private val authenticationToken : AtomicReference[Option[AuthenticationToken]] = new AtomicReference[Option[AuthenticationToken]](None)

  private def getSigningKey(): ApnsSigningKey = ApnsSigningKey.loadFromInputStream(
			new ByteArrayInputStream(certificate.getBytes(StandardCharsets.UTF_8)),
			teamId,
			keyId
		)

  private def refreshToken(): String = {
    val signingKey = getSigningKey()
    val newToken = new AuthenticationToken(signingKey, Instant.now())
    this.authenticationToken.set(Some(newToken))
    newToken.getAuthorizationHeader.toString()
  }

  def getAccessToken(): String = {
    authenticationToken.get() match {
      case Some(token) if token.getIssuedAt().plusSeconds(30 * 60).isAfter(Instant.now()) =>
        token.getAuthorizationHeader.toString()
      case _ => refreshToken()
    }
  }
}