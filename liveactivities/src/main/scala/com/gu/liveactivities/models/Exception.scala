package com.gu.liveactivities.models

class LiveActivityDataException(id: String, message: String) extends Exception(s"Live activity data error for id $id: $message")

class RepositoryException(message: String) extends Exception(message) {
  def this(cause: Throwable, message: String) = {
    this(message)
    initCause(cause)
  }
}

class LiveActivityInvalidStateException(id: String, message: String) extends Exception(s"Live activity invalid state for id $id: $message")