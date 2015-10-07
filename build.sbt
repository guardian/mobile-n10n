import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

lazy val common = project.settings(
  resolvers ++= Seq(
    "Guardian GitHub Releases" at "http://guardian.github.com/maven/repo-releases",
    "Guardian GitHub Snapshots" at "http://guardian.github.com/maven/repo-snapshots",
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
  ),
  libraryDependencies ++= Seq(
    json,
    ws,
    "com.microsoft.azure" % "azure-servicebus" % "0.7.0",
    "org.scalaz" %% "scalaz-core" % "7.1.0",
    "joda-time" % "joda-time" % "2.8.2",
    "com.gu" %% "configuration" %  "4.1"
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

lazy val notification = project.
  dependsOn(common).
  enablePlugins(PlayScala, RiffRaffArtifact, JavaAppPackaging).
  settings(
    fork in run := true,
    routesImport += "binders._",
    routesImport += "models.Topic",
    riffRaffPackageType := (packageZipTarball in config("universal")).value,
    version := "1.0-SNAPSHOT"
  )

lazy val report = project.
  dependsOn(common).
  enablePlugins(PlayScala, RiffRaffArtifact, JavaAppPackaging).
  settings(
    fork in run := true,
    routesImport += "binders._",
    routesImport += "org.joda.time.DateTime",
    riffRaffPackageType := (packageZipTarball in config("universal")).value,
    version := "1.0-SNAPSHOT"
  )

lazy val root = (project in file(".")).
  dependsOn(registration, notification, report, common).
  aggregate(registration, notification, report, common)

addCommandAlias("dist", ";riffRaffArtifact")
