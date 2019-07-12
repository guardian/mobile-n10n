package com.gu.mobile.notifications.client.models

sealed trait Healthcheck
case object Ok extends Healthcheck
case class Unhealthy(errorCode: Option[Int] = None) extends Healthcheck