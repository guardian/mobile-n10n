package football.lib

import scala.concurrent.{ExecutionContext, Future}

// this is a simple way to limit how we're using resources
// without falling into using rate limiting or more complex thread pooling stuff
object Batch {
  def process[E, R](
    elements: List[E],
    batchSize: Int
  )(processOne: E => Future[R])
  (implicit ec: ExecutionContext): Future[List[R]] = {

    def batchProcessing(agg: List[R], remainingBatches: List[List[E]]): Future[List[R]] = {
      remainingBatches.headOption match {
        case Some(batch) => Future.traverse(batch)(processOne)
          .flatMap { result => batchProcessing(agg ++ result, remainingBatches.tail)}
        case None => Future.successful(agg)
      }
    }

    val batches = elements.grouped(batchSize).toList
    batchProcessing(Nil, batches)
  }
}
