import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

val projectVersion = "1.0-latest"

scalaVersion in ThisBuild := "2.12.4"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:implicitConversions")

val standardSettings = Seq[Setting[_]](

  updateOptions := updateOptions.value.withCachedResolution(true),

  riffRaffManifestProjectName := s"mobile-n10n:${name.value}",
  riffRaffManifestBranch := Option(System.getenv("BRANCH_NAME")).getOrElse("unknown_branch"),
  riffRaffBuildIdentifier := Option(System.getenv("BUILD_NUMBER")).getOrElse("DEV"),
  riffRaffManifestVcsUrl  := "git@github.com/guardian/mobile-n10n.git",
  riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
  riffRaffUploadManifestBucket := Option("riffraff-builds"),
  riffRaffArtifactPublishPath := name.value
)

//Common project
lazy val common = project
  .settings(LocalDynamoDB.settings)
  .settings(standardSettings: _*)
  .settings(
    resolvers ++= Seq(
      "Guardian GitHub Releases" at "http://guardian.github.com/maven/repo-releases",
      "Guardian GitHub Snapshots" at "http://guardian.github.com/maven/repo-snapshots",
      "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms",
      "Guardian Frontend Bintray" at "https://dl.bintray.com/guardian/frontend"
    ),
    libraryDependencies ++= Seq(
      ws,
      "com.microsoft.azure" % "azure-servicebus" % "0.7.0",
      "org.typelevel" %% "cats-core" % "1.0.1",
      "joda-time" % "joda-time" % "2.8.2",
      "com.gu" %% "configuration" % "4.1",
      "io.spray" %% "spray-caching" % "1.3.3",
      "com.typesafe.play" %% "play-json" % "2.6.8",
      "com.typesafe.play" %% "play-json-joda" % "2.6.8",
      "com.typesafe.play" %% "play-logback" % "2.6.11",
      "com.gu" %% "pa-client" % "6.0.2",
      "com.gu" %% "simple-s3-configuration" % "1.0",
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.60",
      "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
      "org.specs2" %% "specs2-core" % "3.8.5" % "test",
      "org.specs2" %% "specs2-cats" % "3.8.5" % "test"
    ),
    startDynamoDBLocal := startDynamoDBLocal.dependsOn(compile in Test).value,
    test in Test := (test in Test).dependsOn(startDynamoDBLocal).value,
    testOnly in Test := (testOnly in Test).dependsOn(startDynamoDBLocal).evaluated,
    testQuick in Test := (testQuick in Test).dependsOn(startDynamoDBLocal).evaluated,
    testOptions in Test += dynamoDBLocalTestCleanup.value
  )

lazy val backup = project
  .dependsOn(common)
  .enablePlugins(RiffRaffArtifact)
  .settings(standardSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-logback" % "2.5.3",
      "com.microsoft.azure" % "azure-storage" % "3.1.0"
    ),
    assemblyJarName := s"${name.value}.jar",
    riffRaffPackageType := assembly.value,
    riffRaffArtifactResources += (file(s"${name.value}/cfn.yaml"), s"${name.value}-cfn/cfn.yaml"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _ *) => MergeStrategy.discard
      case PathList("reference.conf") => MergeStrategy.concat
      case x => MergeStrategy.last
    },
    version := "1.0-SNAPSHOT"
  )

lazy val registration = project
  .dependsOn(common % "test->test;compile->compile")
  .enablePlugins(SystemdPlugin, PlayScala, RiffRaffArtifact, JDebPackaging)
  .settings(standardSettings: _*)
  .settings(
    fork in run := true,
    routesImport ++= Seq(
      "binders.querystringbinders._",
      "binders.pathbinders._",
      "models._",
      "models.pagination._"
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val notification = project
  .dependsOn(common)
  .enablePlugins(SystemdPlugin, PlayScala, RiffRaffArtifact, JDebPackaging)
  .settings(standardSettings: _*)
  .settings(
    fork in run := true,
    routesImport ++= Seq(
      "binders.querystringbinders._",
      "binders.pathbinders._",
      "models._"
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val report = project
  .dependsOn(common % "test->test;compile->compile")
  .enablePlugins(SystemdPlugin, PlayScala, RiffRaffArtifact, JDebPackaging)
  .settings(standardSettings: _*)
  .settings(
    fork in run := true,
    routesImport ++= Seq(
      "binders.querystringbinders._",
      "binders.pathbinders._",
      "org.joda.time.DateTime",
      "models._"
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val root = (project in file(".")).
  dependsOn(registration, notification, report, backup, common).
  aggregate(registration, notification, report, backup, common)
