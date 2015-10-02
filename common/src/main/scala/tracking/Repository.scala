package tracking

import scalaz.\/

case class RepositoryError(message: String)

object Repository {
  type RepositoryResult[T] = RepositoryError \/ T
}

object RepositoryResult {
  def apply[T](result: T): RepositoryError \/ T = \/.right(result)
}
