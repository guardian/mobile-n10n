package application

import java.io.File

import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.ApplicationLoader.Context
import play.api.test.Helpers
import play.api.{Application, ApplicationLoader, BuiltInComponents, Environment, LoggerConfigurator, Mode}

trait WithPlayApp extends Around with Scope {
  lazy val app: Application = new TestApplicationLoader(configureComponents).load(context)

  private def context = ApplicationLoader.createContext(
    new Environment(new File("."), classOf[TestApplicationLoader].getClassLoader, Mode.Test)
  )

  override def around[T: AsResult](t: => T): Result = {
    Helpers.running(app)(AsResult.effectively(t))
  }

  def configureComponents(context: Context): BuiltInComponents
}

class TestApplicationLoader(configureComponents: Context => BuiltInComponents) extends ApplicationLoader {
  override def load(context: Context): Application = {
    configureLoggers(context)
    configureComponents(context).application
  }

  private def configureLoggers(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
  }
}
