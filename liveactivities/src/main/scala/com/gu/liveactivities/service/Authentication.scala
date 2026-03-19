package com.gu.liveactivities.service

import java.util.Date
import java.util.concurrent.atomic._
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.auth.AuthenticationToken
import org.checkerframework.checker.units.qual.A
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
    val newToken = new AuthenticationToken(signingKey, new Date())
    this.authenticationToken.set(Some(newToken))
    newToken.getAuthorizationHeader().toString()
  }

  def getAccessToken(): String = {
    authenticationToken.get() match {
      case Some(token) if Date.from(token.getIssuedAt().toInstant().plusSeconds(30 * 60)).after(new Date()) => 
        token.getAuthorizationHeader().toString()
      case _ => refreshToken()
    }
  }
}