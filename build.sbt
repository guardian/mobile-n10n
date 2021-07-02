import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import play.sbt.PlayImport.specs2
import sbt.Keys.{libraryDependencies, mainClass}
import sbtassembly.AssemblyPlugin.autoImport.{assemblyJarName, assemblyMergeStrategy}
import sbtassembly.MergeStrategy

val projectVersion = "1.0-latest"

organization := "com.gu"
scalaVersion in ThisBuild := "2.13.2"

val compilerOptions = Seq(
  "-deprecation",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:implicitConversions"
)

scalacOptions in ThisBuild ++= compilerOptions

val playJsonVersion = "2.8.1"
val specsVersion: String = "4.5.1"
val awsSdkVersion: String = "1.11.772"
val doobieVersion: String = "0.9.2"
val catsVersion: String = "2.1.1"
val okHttpVersion: String = "3.14.8"
val paClientVersion: String = "7.0.5"
val apacheThrift: String = "0.14.0"
val jacksonDatabind: String = "2.10.5.1"
val jacksonCbor: String = "2.12.1"
val jacksonScalaModule: String = "2.12.3"
val simpleConfigurationVersion: String = "1.5.6"

val standardSettings = Seq[Setting[_]](
  resolvers ++= Seq(
    "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    "Guardian GitHub Snapshots" at "https://guardian.github.com/maven/repo-snapshots"
  ),
  riffRaffManifestProjectName := s"mobile-n10n:${name.value}",
  riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
  riffRaffUploadManifestBucket := Option("riffraff-builds"),
  libraryDependencies ++= Seq(
    "com.github.nscala-time" %% "nscala-time" % "2.24.0",
    "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided",
    specs2 % Test,
    "org.specs2" %% "specs2-matcher-extra" % specsVersion % Test
  ),
  // Workaround Mockito causes deadlock on SBT classloaders: https://github.com/sbt/sbt/issues/3022
  parallelExecution in Test := false
)

lazy val commoneventconsumer = project
  .settings(Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "org.specs2" %% "specs2-core" % specsVersion % "test",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabind,
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonCbor,
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.13" % jacksonScalaModule
    ),
  ))

lazy val commontest = project
  .settings(Seq(
    libraryDependencies ++= Seq(
      specs2,
      playCore,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabind,
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonCbor,
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.13" % jacksonScalaModule
    ),
  ))


lazy val common = project
  .dependsOn(commoneventconsumer)
  .settings(LocalDynamoDBCommon.settings)
  .settings(standardSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      ws,
      "org.typelevel" %% "cats-core" % catsVersion,
      "joda-time" % "joda-time" % "2.9.9",
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.typesafe.play" %% "play-json-joda" % playJsonVersion,
      "com.gu" %% "pa-client" % paClientVersion,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
      "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2",
      "ai.x" %% "play-json-extensions" % "0.42.0",
      "org.tpolecat" %% "doobie-core"      % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"    % doobieVersion,
      "org.tpolecat" %% "doobie-postgres"  % doobieVersion,
      "org.tpolecat" %% "doobie-specs2"    % doobieVersion % Test,
      "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test,
      "org.tpolecat" %% "doobie-h2"        % doobieVersion % Test,
      "com.gu" %% "mobile-logstash-encoder" % "1.1.2",
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion
    ),
    fork := true,
    startDynamoDBLocal := startDynamoDBLocal.dependsOn(compile in Test).value,
    test in Test := (test in Test).dependsOn(startDynamoDBLocal).value,
    testOnly in Test := (testOnly in Test).dependsOn(startDynamoDBLocal).evaluated,
    testQuick in Test := (testQuick in Test).dependsOn(startDynamoDBLocal).evaluated,
    testOptions in Test += dynamoDBLocalTestCleanup.value,
    // the following option is to allow tests using wsClient such as NotificationHubClientSpec
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "sequential", "true")
  )

lazy val commonscheduledynamodb = project
  .settings(LocalDynamoDBScheduleLambda.settings)
  .settings(List(
    libraryDependencies ++= List(
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabind,
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonCbor,
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.13" % jacksonScalaModule,
      specs2 % Test

    ),
    test in Test := (test in Test).dependsOn(startDynamoDBLocal).value,
    testOnly in Test := (testOnly in Test).dependsOn(startDynamoDBLocal).evaluated,
    testQuick in Test := (testQuick in Test).dependsOn(startDynamoDBLocal).evaluated,
    testOptions in Test += dynamoDBLocalTestCleanup.value
  ))

lazy val registration = project
  .dependsOn(common, commontest % "test->test")
  .enablePlugins(SystemdPlugin, PlayScala, RiffRaffArtifact, JDebPackaging)
  .settings(standardSettings: _*)
  .settings(
    fork := true,
    routesImport ++= Seq(
      "binders.querystringbinders._",
      "binders.pathbinders._",
      "models._"
    ),
    libraryDependencies ++= Seq(
      logback,
      "org.tpolecat" %% "doobie-h2"        % doobieVersion % Test
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffArtifactResources += (file(s"registration/conf/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val notification = project
  .dependsOn(common)
  .dependsOn(commonscheduledynamodb)
  .enablePlugins(SystemdPlugin, PlayScala, RiffRaffArtifact, JDebPackaging)
  .settings(standardSettings: _*)
  .settings(
    fork := true,
    routesImport ++= Seq(
      "binders.querystringbinders._",
      "binders.pathbinders._",
      "models._"
    ),
    libraryDependencies ++= Seq(
      logback,
      "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffArtifactResources += (file(s"notification/conf/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val report = project
  .dependsOn(common, commontest % "test->test")
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
    libraryDependencies ++= Seq(
      logback
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffArtifactResources += (file(s"report/conf/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val apiModels = {
  import sbt.Keys.organization
  import sbtrelease._
  import ReleaseStateTransformations._
  Project("api-models", file("api-models")).settings(Seq(
    name := "mobile-notifications-api-models",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "org.specs2" %% "specs2-core" % specsVersion % "test",
      "org.specs2" %% "specs2-mock" % specsVersion % "test",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabind,
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonCbor,
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.13" % jacksonScalaModule
    ),
    organization := "com.gu",
    publishTo := sonatypePublishToBundle.value,

    scmInfo := Some(ScmInfo(
      url("https://github.com/guardian/mobile-n10n"),
      "scm:git:git@github.com:guardian/mobile-n10n.git"
    )),

    homepage := Some(url("https://github.com/guardian/mobile-n10n")),

    developers := List(Developer(
      id = "Guardian",
      name = "Guardian",
      email = null,
      url = url("https://github.com/guardian")
    )),
    description := "Scala models for the Guardian Push Notifications API",
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    releaseVersionFile := file("api-models/version.sbt"),
    licenses := Seq("Apache V2" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  ))
}

def lambda(projectName: String, directoryName: String, mainClassName: Option[String] = None): Project =
  Project(projectName, file(directoryName))
  .enablePlugins(RiffRaffArtifact, AssemblyPlugin)
  .settings(
    organization := "com.gu",
    resolvers ++= Seq(
      "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
      "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.2.0",
      "org.slf4j" % "slf4j-api" % "1.7.30",
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.13.3",
      "com.gu" %% "simple-configuration-core" % simpleConfigurationVersion,
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      specs2 % Test
    ),
    assemblyJarName := s"$projectName.jar",
    assemblyMergeStrategy in assembly := {
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat" => new MergeLog4j2PluginCachesStrategy
      case _ => MergeStrategy.first
    },
    fork in (Test, run) := true,
    scalacOptions := compilerOptions,
    riffRaffPackageType := assembly.value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := s"mobile-n10n:$projectName",
    mainClass := mainClassName,
    // Workaround Mockito causes deadlock on SBT classloaders: https://github.com/sbt/sbt/issues/3022
    parallelExecution in Test := false
  )

lazy val schedulelambda = lambda("schedule", "schedulelambda")
  .dependsOn(commonscheduledynamodb)
  .settings {
    List(
      libraryDependencies ++= Seq(
        "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
        "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
        "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
        "org.specs2" %% "specs2-core" % specsVersion % "test",
        "org.specs2" %% "specs2-scalacheck" % specsVersion % "test",
        "org.specs2" %% "specs2-mock" % specsVersion % "test"
      ),
      excludeDependencies ++= Seq(
        ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
      ),
      riffRaffArtifactResources += (file(s"schedulelambda/cfn.yaml"), s"${name.value}-cfn/cfn.yaml"),
    )
  }

lazy val football = lambda("football", "football")
  .dependsOn(apiModels  % "test->test", apiModels  % "compile->compile")
  .settings(
    resolvers += "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.25",
      "com.typesafe" % "config" % "1.3.2",
      "org.scanamo" %% "scanamo" % "1.0.0-M12",
      "org.scanamo" %% "scanamo-testkit" % "1.0.0-M12" % "test",
      "com.gu" %% "content-api-client-default" % "15.9",
      "org.apache.thrift" % "libthrift" % apacheThrift,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "com.gu" %% "pa-client" % paClientVersion,
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "com.google.code.findbugs" % "jsr305" % "3.0.2",
      "org.specs2" %% "specs2-core" % specsVersion % "test",
      "org.specs2" %% "specs2-mock" % specsVersion % "test"
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    ),
    riffRaffArtifactResources += (baseDirectory.value / "cfn.yaml", "mobile-notifications-football-cfn/cfn.yaml")
  )

lazy val eventconsumer = lambda("eventconsumer", "eventconsumer", Some("com.gu.notifications.events.LocalRun"))
  .dependsOn(commoneventconsumer)
  .settings({
    Seq(
      description := "Consumes events produced when an app receives a notification",
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play-json" % playJsonVersion,
        "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
        "com.amazonaws" % "aws-java-sdk-athena" % awsSdkVersion,
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1"
      ),
      riffRaffArtifactResources += ((baseDirectory.value / "cfn.yaml"), s"mobile-notifications-eventconsumer-cfn/cfn.yaml")
    )
  })

lazy val notificationworkerlambda = lambda("notificationworkerlambda", "notificationworkerlambda", Some("com.gu.notifications.worker.TopicCounterLocalRun"))
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.turo" % "pushy" % "0.13.10",
      "com.google.firebase" % "firebase-admin" % "6.16.0",
      "io.netty" % "netty-codec" % "4.1.46.Final",
      "io.netty" % "netty-codec-http" % "4.1.44.Final",
      "com.amazonaws" % "aws-lambda-java-events" % "2.2.8",
      "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "com.typesafe.play" %% "play-json" % playJsonVersion
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    ),
    riffRaffArtifactResources += (baseDirectory.value / "harvester-cfn.yaml", s"mobile-notifications-harvester-cfn/harvester-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"mobile-notifications-ios-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"mobile-notifications-android-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"mobile-notifications-ios-edition-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"mobile-notifications-android-edition-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"mobile-notifications-android-beta-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "registration-cleaning-worker-cfn.yaml", s"mobile-notifications-registration-cleaning-worker-cfn/registration-cleaning-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "topic-counter-cfn.yaml", s"mobile-notifications-topic-counter-cfn/topic-counter-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "expired-registration-cleaner-cfn.yaml", s"mobile-notifications-expired-registration-cleaner-cfn/expired-registration-cleaner-cfn.yaml")
  )

lazy val fakebreakingnewslambda = lambda("fakebreakingnewslambda", "fakebreakingnewslambda", Some("fakebreakingnews.LocalRun"))
  .dependsOn(common)
  .dependsOn(apiModels  % "test->test", apiModels  % "compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    ),
    riffRaffArtifactResources += (baseDirectory.value / "fakebreakingnewslambda-cfn.yaml", "fakebreakingnewslambda-cfn/fakebreakingnewslambda-cfn.yaml")
  )

lazy val reportExtractor = lambda("reportextractor", "reportextractor", Some("com.gu.notifications.extractor.LocalRun"))
  .dependsOn(common)
  .settings(
    riffRaffArtifactResources += (baseDirectory.value / "cfn.yaml", "reportextractor-cfn/cfn.yaml"),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    )
  )
