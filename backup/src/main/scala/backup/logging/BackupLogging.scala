package backup.logging

import org.slf4j.{Logger, LoggerFactory}


trait BackupLogging {

  val logger: Logger =  LoggerFactory.getLogger(this.getClass)

}
