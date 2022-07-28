package com.gu.notifications.workerlambda.delivery.utils

object TimeToLive {
  val DefaulTtl: Long = 86400000L //24 hours
  val BreakingNewsTtl: Long = 3600000L //12 hours
  val FootballMatchStatusTtl: Long = 600000L //10 minutes
}
