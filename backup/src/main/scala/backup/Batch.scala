package backup

import scala.concurrent.Future

trait Batch {
  def execute(): Future[Unit]
}
