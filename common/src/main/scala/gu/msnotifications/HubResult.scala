package gu.msnotifications

sealed trait HubResult[+T] {
  def map[V](f: T => V): HubResult[V]
}

object HubResult {

  case class Successful[T](value: T) extends HubResult[T] {
    override def map[V](f: (T) => V): HubResult[V] = Successful(f(value))
  }

  sealed trait Failure[T] extends HubResult[T]

  case class ServiceError[T](reason: String, code: Int) extends Failure[T] {
    override def map[V](f: (T) => V): HubResult[V] =
      ServiceError(reason = reason, code = code)
  }

  case class ServiceParseFailed[T](body: String, reason: String) extends Failure[T] {
    override def map[V](f: (T) => V): HubResult[V] =
      ServiceParseFailed(body = body, reason = reason)
  }

}
