package com.gu.notifications.extractor

object LocalRun {
  def main(args: Array[String]): Unit = {
    new Lambda().handleRequest(DateRange(None, None), null)
  }
}
