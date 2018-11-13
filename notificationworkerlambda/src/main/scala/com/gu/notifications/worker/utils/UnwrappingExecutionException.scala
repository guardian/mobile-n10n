package com.gu.notifications.worker.utils

import java.util.concurrent.ExecutionException

object UnwrappingExecutionException {
  def unapply(exception: Throwable): Option[Throwable] = exception match {
    case e: ExecutionException if e.getCause != null => unapply(e.getCause)
    case e => Some(e)
  }
}
