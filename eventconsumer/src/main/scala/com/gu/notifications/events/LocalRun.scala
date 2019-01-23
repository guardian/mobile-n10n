package com.gu.notifications.events

object LocalRun extends App {
  new AthenaLambda().handleRequestLocally()
}
