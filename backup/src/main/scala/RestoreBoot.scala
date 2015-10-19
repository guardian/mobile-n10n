import batches.{Restore, Batch}

object RestoreBoot {
  def main (args: Array[String]): Unit = Batch.run[Restore]("./backup")
}
