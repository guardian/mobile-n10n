package batches

import com.microsoft.azure.storage.blob.{CloudBlob, CloudBlobDirectory}
import collection.JavaConversions._

object AzureUtils {
  def listAllFiles(directory: CloudBlobDirectory): List[CloudBlob] = {
    // toList to force the lazy list to be evaluated
    val directoryContent = directory.listBlobs().toList
    directoryContent.flatMap {
      case blob: CloudBlob => List(blob)
      case dir: CloudBlobDirectory => listAllFiles(dir)
      case _ => Nil
    }
  }
}
