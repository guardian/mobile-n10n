import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import play.sbt.PlayImport.specs2
import sbt.Keys.{libraryDependencies, mainClass}
import sbtassembly.AssemblyPlugin.autoImport.{assemblyJarName, assemblyMergeStrategy}
import sbtassembly.MergeStrategy

val projectVersion = "1.0-latest"


organization := "com.gu"
scalaVersion in ThisBuild := "2.12.6"

// this prevents deadlocks when running the tests from the root project.
// See https://github.com/sbt/sbt/issues/3022
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

val compilerOptions = Seq(
  "-deprecation",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-Ypartial-unification"
)

scalacOptions in ThisBuild ++= compilerOptions

val minJacksonVersion: String = "2.8.9"
val minJacksonLibs = Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % minJacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % minJacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % minJacksonVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % minJacksonVersion
)

val playJsonVersion = "2.6.9"
val specsVersion: String = "4.0.3"
val awsSdkVersion: String = "1.11.433"
val doobieVersion: String = "0.6.0"
val catsVersion: String = "1.4.0"
val simpleConfigurationVersion: String = "1.5.0"
val okHttpVersion: String = "3.14.0"

val standardSettings = Seq[Setting[_]](
  resolvers ++= Seq(
    "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    "Guardian GitHub Snapshots" at "https://guardian.github.com/maven/repo-snapshots",
    "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms",
    "Guardian Frontend Bintray" at "https://dl.bintray.com/guardian/frontend"
  ),
  riffRaffManifestProjectName := s"mobile-n10n:${name.value}",
  riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
  riffRaffUploadManifestBucket := Option("riffraff-builds"),
  libraryDependencies ++= Seq(
    "com.github.nscala-time" %% "nscala-time" % "2.18.0",
    "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided",
    specs2 % Test,
    "org.specs2" %% "specs2-matcher-extra" % "3.8.9" % Test
  )
)

lazy val commoneventconsumer = project
  .settings(Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "org.specs2" %% "specs2-core" % specsVersion % "test"
    )
  ))

lazy val commontest = project
  .settings(Seq(
    libraryDependencies ++= Seq(
      specs2,
      playCore
    )
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
      "com.gu" %% "pa-client" % "6.1.0",
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
      "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2",
      "ai.x" %% "play-json-extensions" % "0.10.0",
      "org.tpolecat" %% "doobie-core"      % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"    % doobieVersion,
      "org.tpolecat" %% "doobie-postgres"  % doobieVersion,
      "org.tpolecat" %% "doobie-specs2"    % doobieVersion % Test,
      "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test,
      "org.tpolecat" %% "doobie-h2"        % doobieVersion % Test,
      "com.gu" %% "mobile-logstash-encoder" % "1.0.2"
    ),
    libraryDependencies ++= minJacksonLibs,
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
      specs2 % Test

    ),
    libraryDependencies ++= minJacksonLibs,
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
      "models._",
      "models.pagination._"
    ),
    libraryDependencies ++= Seq(
      logback,
      "org.tpolecat" %% "doobie-h2"        % doobieVersion % Test
    ),
    riffRaffPackageType := (packageBin in Debian).value,
    riffRaffArtifactResources += (file(s"common/cfn/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
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
    riffRaffArtifactResources += (file(s"common/cfn/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
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
    riffRaffArtifactResources += (file(s"common/cfn/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
    packageName in Debian := name.value,
    version := projectVersion
  )

lazy val apiClient = {
  import sbt.Keys.organization
  import sbtrelease._
  import ReleaseStateTransformations._
  Project("api-client", file("api-client")).settings(Seq(
    name := "mobile-notifications-client",
    resolvers ++= Seq(
      "Guardian GitHub Releases" at "http://guardian.github.io/maven/repo-releases",
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "org.specs2" %% "specs2-core" % specsVersion % "test",
      "org.specs2" %% "specs2-mock" % specsVersion % "test"
    ),
    organization := "com.gu",
    bintrayOrganization := Some("guardian"),
    bintrayRepository := "mobile",
    description := "Scala client for the Guardian Push Notifications API",
    scmInfo := Some(ScmInfo(
      url("https://github.com/guardian/mobile-n10n"),
      "scm:git:git@github.com:guardian/mobile-n10n.git"
    )), pomExtra in Global := {
      <url>https://github.com/guardian/mobile-notifications-api-client</url>
        <developers>
          <developer>
            <id>@guardian</id>
            <name>The guardian</name>
            <url>https://github.com/guardian</url>
          </developer>
        </developers>
    },
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    releaseVersionFile := file("api-client/version.sbt"),
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
      releaseStepTask(bintrayRelease),
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
    resolvers += "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
      "com.amazonaws" % "aws-lambda-java-log4j2" % "1.1.0",
      "org.slf4j" % "slf4j-api" % "1.7.26",
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.11.2",
      "com.gu" %% "simple-configuration-core" % simpleConfigurationVersion,
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      specs2 % Test
    ),
    assemblyJarName := s"$projectName.jar",
    assemblyMergeStrategy in assembly := {
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    fork in (Test, run) := true,
    scalacOptions := compilerOptions,
    riffRaffPackageType := assembly.value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := s"mobile-n10n:$projectName",
    mainClass := mainClassName
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
      riffRaffArtifactResources += (file(s"common/cfn/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
    )
  }

lazy val eventconsumer = lambda("eventconsumer", "eventconsumer", Some("com.gu.notifications.events.LocalRun"))
  .dependsOn(commoneventconsumer)
  .settings({
    Seq(
      description := "Consumes events produced when an app receives a notification",
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play-json" % playJsonVersion,
        "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
        "com.amazonaws" % "aws-java-sdk-athena" % awsSdkVersion,
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
      ),
      riffRaffArtifactResources += ((baseDirectory.value / "cfn.yaml"), s"${name.value}-cfn/cfn.yaml")
    )
  })

lazy val notificationworkerlambda = lambda("notificationworkerlambda", "notificationworkerlambda", Some("com.gu.notifications.worker.TopicCounterLocalRun"))
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      "com.turo" % "pushy" % "0.13.5",
      "com.google.firebase" % "firebase-admin" % "6.3.0",
      "com.amazonaws" % "aws-lambda-java-events" % "2.2.2",
      "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion
    ),
    riffRaffArtifactResources += (baseDirectory.value / "harvester-cfn.yaml", s"harvester-cfn/harvester-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"ios-notification-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "sender-worker-cfn.yaml", s"android-notification-worker-cfn/sender-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "registration-cleaning-worker-cfn.yaml", s"registration-cleaning-worker-cfn/registration-cleaning-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "topic-counter-cfn.yaml", s"topic-counter-cfn/topic-counter-cfn.yaml")
  )

lazy val fakebreakingnewslambda = lambda("fakebreakingnewslambda", "fakebreakingnewslambda", Some("fakebreakingnews.LocalRun"))
  .dependsOn(common)
  .dependsOn(apiClient  % "test->test", apiClient  % "compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
    ),
    riffRaffArtifactResources += (baseDirectory.value / "fakebreakingnewslambda-cfn.yaml", "fakebreakingnewslambda-cfn/fakebreakingnewslambda-cfn.yaml")
  )

lazy val reportExtractor = lambda("reportextractor", "reportextractor", Some("com.gu.notifications.extractor.LocalRun"))
  .dependsOn(common)
  .settings(
    riffRaffArtifactResources += (baseDirectory.value / "reportextractor-cfn.yaml", "reportextractor-cfn/reportextractor-cfn.yaml")
  )

lazy val root = (project in file(".")).
  aggregate(
    registration,
    notification,
    report,
    common,
    commonscheduledynamodb,
    schedulelambda,
    apiClient,
    eventconsumer,
    notificationworkerlambda,
    fakebreakingnewslambda,
    reportExtractor
  )
