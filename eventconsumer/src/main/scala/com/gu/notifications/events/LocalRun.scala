package com.gu.notifications.events

object LocalRun extends App {
  if (args.size < 1) {
    System.out.println("No argument")
  }
  else {
    args(0) match {
      case "athena" => {
        new AthenaLambda().handleRequestLocally()
      }
    }
  }


}
