import batches.{Backup, Batch}

object BackupBoot {
  def main (args: Array[String]): Unit = Batch.run[Backup]("./backup")
}
