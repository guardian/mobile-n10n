package com.gu.notifications.worker.models

import com.gu.notifications.worker.delivery.DeliveryException.{DryRun, InvalidToken}

case class SendingResults(
  successCount: Int,
  failureCount: Int,
  dryRunCount: Int,
  invalidTokens: InvalidTokens
) {
  def total: Int = successCount + failureCount + dryRunCount
  override def toString: String =
    s"Success: $successCount, Failure: $failureCount, DryRun: $dryRunCount Total: $total"
}

object SendingResults {
  def empty = new SendingResults(0, 0, 0, InvalidTokens.empty)

  def aggregate(previous: SendingResults, res: Either[_, _]) = res match {
    case Right(_) => previous.copy(successCount = previous.successCount + 1)
    case Left(DryRun(_, _)) => previous.copy(dryRunCount = previous.dryRunCount + 1)
    case Left(InvalidToken(_, token, _, _)) =>
      val invalidTokens = previous.invalidTokens.copy(tokens = token :: previous.invalidTokens.tokens)
      previous.copy(failureCount = previous.failureCount + 1, invalidTokens = invalidTokens)
    case Left(_) => previous.copy(failureCount = previous.failureCount + 1)
  }
}

