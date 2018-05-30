import java.io.ByteArrayOutputStream
import java.nio.file.Files

import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import sbt.File
import sbtassembly.MergeStrategy

import scala.collection.JavaConverters.asJavaEnumerationConverter

class MergeLog4j2PluginCachesStrategy extends MergeStrategy {
  override def name: String = "MergeLog4j2PluginCachesStrategy"

  override def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val pluginCache = new PluginCache
    pluginCache.loadCacheFiles(files.map(_.toURI.toURL).toIterator.asJavaEnumeration)
    val baos = new ByteArrayOutputStream // avoid worrying about closeable resources
    pluginCache.writeCache(baos)
    val mergeFile = MergeStrategy.createMergeTarget(tempDir, path)
    Files.write(mergeFile.toPath, baos.toByteArray)
    Right(Seq(mergeFile -> path))
  }
}