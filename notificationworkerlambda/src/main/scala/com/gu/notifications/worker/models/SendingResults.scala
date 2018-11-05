package com.gu.notifications.worker.models

case class SendingResults(successCount: Int, failureCount: Int) {
  override def toString: String = s"Success: $successCount, Failure: $failureCount, Total: ${successCount + failureCount}"
}

object SendingResults {
  def empty = new SendingResults(0, 0)

  def inc(previous: SendingResults, res: Either[_, _]) = res match {
    case Right(_) => previous.copy(successCount = previous.successCount + 1)
    case Left(_) => previous.copy(failureCount = previous.failureCount + 1)
  }
}

