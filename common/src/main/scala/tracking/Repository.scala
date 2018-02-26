package tracking

case class RepositoryError(message: String)

object Repository {
  type RepositoryResult[T] = Either[RepositoryError, T]
}

object RepositoryResult {
  def apply[T](result: T): Either[RepositoryError, T] = Right(result)
}
