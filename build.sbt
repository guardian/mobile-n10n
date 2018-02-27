import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

val projectVersion = "1.0-latest"


organization := "com.gu"
scalaVersion in ThisBuild := "2.12.4"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:implicitConversions")

val standardSettings = Seq[Setting[_]](
  riffRaffManifestProjectName := s"mobile-n10n:${name.value}",
  riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
  riffRaffUploadManifestBucket := Option("riffraff-builds"),
  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.1.0",
    "org.scala-lang.modules" %% "scala-async" % "0.9.7",
    "com.github.nscala-time" %% "nscala-time" % "2.18.0",
    "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided",
    specs2 % Test,
    "org.specs2" %% "specs2-matcher-extra" % "3.8.9" % Test
  )
)

lazy val common = project
  .settings(LocalDynamoDB.settings)
  .settings(standardSettings: _*)
  .settings(
    resolvers ++= Seq(
      "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
      "Guardian GitHub Snapshots" at "https://guardian.github.com/maven/repo-snapshots",
      "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms",
      "Guardian Frontend Bintray" at "https://dl.bintray.com/guardian/frontend"
    ),
    libraryDependencies ++= Seq(
      ws,
      "com.microsoft.azure" % "azure-servicebus" % "1.1.1",
      "org.typelevel" %% "cats-core" % "1.0.1",
      "joda-time" % "joda-time" % "2.9.9",
      "com.typesafe.play" %% "play-json" % "2.6.8",
      "com.typesafe.play" %% "play-json-joda" % "2.6.8",
      "com.typesafe.play" %% "play-logback" % "2.6.11",
      "com.gu" %% "pa-client" % "6.1.0",
      "com.gu" %% "simple-configuration-ssm" % "1.4.1",
      "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.285"
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
    fork := true,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.25",
      "com.microsoft.azure" % "azure-storage" % "7.0.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.0"
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
    fork := true,
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
    fork := true,
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
    fork := true,
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
  aggregate(registration, notification, report, backup, common)
