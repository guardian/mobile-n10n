lazy val core = project.settings(
  libraryDependencies ++= Seq(
    json,
    ws,
    "com.microsoft.azure" % "azure-servicebus" % "0.7.0"
  )
)

lazy val api = project.dependsOn(core).enablePlugins(PlayScala).settings(
  fork in run := true,
  routesImport += "binders._"
)

lazy val root = (project in file(".")).dependsOn(api, core).aggregate(api, core)