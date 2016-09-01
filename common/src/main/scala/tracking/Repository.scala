package tracking

import cats.data.Xor

case class RepositoryError(message: String)

object Repository {
  type RepositoryResult[T] = RepositoryError Xor T
}

object RepositoryResult {
  def apply[T](result: T): RepositoryError Xor T = Xor.right(result)
}
