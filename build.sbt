import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

lazy val common = project.settings(
  libraryDependencies ++= Seq(
    json,
    ws,
    "com.microsoft.azure" % "azure-servicebus" % "0.7.0"
  )
)

lazy val registration = project.
  dependsOn(common).
  enablePlugins(PlayScala, RiffRaffArtifact, JavaAppPackaging).
  settings(
    fork in run := true,
    routesImport += "binders._",
    riffRaffPackageType := (packageZipTarball in config("universal")).value,
    version := "1.0-SNAPSHOT"
  )

lazy val root = (project in file(".")).
  dependsOn(registration, common).
  aggregate(registration, common)

addCommandAlias("dist", ";riffRaffArtifact")
