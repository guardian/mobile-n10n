import java.nio.charset.StandardCharsets
import java.nio.file.Files

import sbt.File
import sbtassembly.MergeStrategy
import scala.collection.JavaConverters._
class MergeMimeTypesStrategy extends MergeStrategy {
  override def name: String = "MergeMimeTypesStrategy"
  override def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val mergeFile = MergeStrategy.createMergeTarget(tempDir, path)
    Files.write(
      mergeFile.toPath,
      files.map(_.toPath)
        .flatMap(Files.readAllLines(_, StandardCharsets.UTF_8).asScala)
        .distinct
        .mkString("\n").getBytes(StandardCharsets.UTF_8))
    Right(Seq(mergeFile -> path))
  }
}