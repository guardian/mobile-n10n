package com.gu.liveactivities.service

import java.util.Date
import java.util.concurrent.atomic._
import com.turo.pushy.apns.auth.ApnsSigningKey
import com.turo.pushy.apns.auth.AuthenticationToken
import org.checkerframework.checker.units.qual.A

object Authentication {

  private val authenticationToken : AtomicReference[Option[AuthenticationToken]] = new AtomicReference[Option[AuthenticationToken]](None)

  private def refreshToken(): String = {
    val signingKey = ApnsSigningKey.loadFromPkcs8File(
      new java.io.File("liveactivities/src/main/resources/AuthKey_N9MYT8RFH4.p8"),
      "998P9U5NGJ",
      "N9MYT8RFH4"
    )
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