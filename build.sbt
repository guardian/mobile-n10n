lazy val common = project.settings(
  libraryDependencies ++= Seq(
    json,
    ws,
    "com.microsoft.azure" % "azure-servicebus" % "0.7.0"
  )
)

lazy val registration = project.
  dependsOn(common).
  enablePlugins(PlayScala).
  settings(
    fork in run := true,
    routesImport += "binders._"
  )

lazy val root = (project in file(".")).
  dependsOn(registration, common).
  aggregate(registration, common)