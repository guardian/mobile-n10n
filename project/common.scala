import sbt._
import Keys._

object CommonSettingsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  override def projectSettings = Seq(
    organization := "com.gu",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-deprecation", "-feature", "-language:postfixOps"),
    version := "1.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0-M7" % Test,
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.0-M7",
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
    libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.5"
  )
}
