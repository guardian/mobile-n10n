import play.sbt.PlayImport.specs2
import sbt.Keys.{libraryDependencies, mainClass}
import sbtassembly.AssemblyPlugin.autoImport.{assemblyJarName, assemblyMergeStrategy}
import sbtassembly.MergeStrategy
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

val projectVersion = "1.0-latest"

organization := "com.gu"
ThisBuild / scalaVersion := "2.13.12"

val compilerOptions = Seq(
  "-deprecation",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:implicitConversions"
)

ThisBuild / scalacOptions ++= compilerOptions

val playJsonVersion = "2.10.1"
val specsVersion: String = "4.8.3"
val awsSdkVersion: String = "1.12.557"
val doobieVersion: String = "0.13.4"
val catsVersion: String = "2.10.0"
val okHttpVersion: String = "4.11.0"
val paClientVersion: String = "7.0.7"
val apacheThrift: String = "0.15.0"
val jacksonDatabind: String = "2.15.2"
val jacksonCbor: String = "2.15.2"
val jacksonScalaModule: String = "2.15.2"
val simpleConfigurationVersion: String = "1.5.6"
val googleOAuthClient: String = "1.34.1"
val nettyVersion: String = "4.1.98.Final"
val slf4jVersion: String = "1.7.36"

val standardSettings = Seq[Setting[_]](
  // We should remove this when all transitive dependencies use the same version of scala-xml
  // For now this isn't considered an issue due to the compatability between 1.2.x and 2.1.x of the library
  libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
  resolvers ++= Seq(
    "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    "Guardian GitHub Snapshots" at "https://guardian.github.com/maven/repo-snapshots"
  ),
  libraryDependencies ++= Seq(
    "com.github.nscala-time" %% "nscala-time" % "2.32.0",
    "com.softwaremill.macwire" %% "macros" % "2.5.9" % "provided",
    specs2 % Test,
    "org.specs2" %% "specs2-matcher-extra" % specsVersion % Test
  ),
  // Workaround Mockito causes deadlock on SBT classloaders: https://github.com/sbt/sbt/issues/3022
  Test / parallelExecution := false
)

lazy val commoneventconsumer = project
  .settings(Seq(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "com.amazonaws" % "aws-java-sdk-athena" % awsSdkVersion,
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
      "joda-time" % "joda-time" % "2.12.5",
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
      "com.gu" %% "mobile-logstash-encoder" % "1.1.6",
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      "io.netty" % "netty-handler" % nettyVersion,
      "io.netty" % "netty-codec" % nettyVersion,
      "io.netty" % "netty-codec-http" % nettyVersion,
      "io.netty" % "netty-codec-http2" % nettyVersion,
      "io.netty" % "netty-common" % nettyVersion,
      "org.postgresql" % "postgresql" % "42.6.0",
    ),
    fork := true,
    startDynamoDBLocal := startDynamoDBLocal.dependsOn(Test / compile).value,
    Test / test := (Test / test).dependsOn(startDynamoDBLocal).value,
    Test / testOnly := (Test / testOnly).dependsOn(startDynamoDBLocal).evaluated,
    Test / testQuick := (Test / testQuick).dependsOn(startDynamoDBLocal).evaluated,
    Test / testOptions += dynamoDBLocalTestCleanup.value,
    // the following option is to allow tests using wsClient such as NotificationHubClientSpec
    Test / testOptions += Tests.Argument(TestFrameworks.Specs2, "sequential", "true")
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
    Test / test := (Test / test).dependsOn(startDynamoDBLocal).value,
    Test / testOnly := (Test / testOnly).dependsOn(startDynamoDBLocal).evaluated,
    Test / testQuick := (Test / testQuick).dependsOn(startDynamoDBLocal).evaluated,
    Test / testOptions += dynamoDBLocalTestCleanup.value
  ))

lazy val registration = project
  .dependsOn(common, commontest % "test->test")
  .enablePlugins(SystemdPlugin, PlayScala, JDebPackaging)
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
    Debian / packageName := name.value,
    version := projectVersion
  )

lazy val notification = project
  .dependsOn(common)
  .dependsOn(commonscheduledynamodb)
  .enablePlugins(SystemdPlugin, PlayScala, JDebPackaging)
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
    Debian / packageName := name.value,
    version := projectVersion
  )

lazy val report = project
  .dependsOn(common, commontest % "test->test")
  .enablePlugins(SystemdPlugin, PlayScala, JDebPackaging)
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
    Debian / packageName := name.value,
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
  .enablePlugins(AssemblyPlugin)
  .settings(
    organization := "com.gu",
    resolvers ++= Seq(
      "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
      "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "com.gu" %% "simple-configuration-core" % simpleConfigurationVersion,
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.11",
      "net.logstash.logback" % "logstash-logback-encoder" % "7.4",
      specs2 % Test
    ),
    assemblyJarName := s"$projectName.jar",
    assembly / assemblyMergeStrategy := {
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    Test / run / fork := true,
    scalacOptions := compilerOptions,
    mainClass := mainClassName,
    // Workaround Mockito causes deadlock on SBT classloaders: https://github.com/sbt/sbt/issues/3022
    Test / parallelExecution := false
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
        "org.specs2" %% "specs2-mock" % specsVersion % "test",
        "io.netty" % "netty-common" % nettyVersion,
        "io.netty" % "netty-codec" % nettyVersion,
        "io.netty" % "netty-codec-http" % nettyVersion,
        "io.netty" % "netty-codec-http2" % nettyVersion
      ),
      excludeDependencies ++= Seq(
        ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
      ),
    )
  }

lazy val football = lambda("football", "football")
  .dependsOn(apiModels  % "test->test", apiModels  % "compile->compile")
  .settings(
    resolvers += "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    libraryDependencies ++= Seq(
      "org.scanamo" %% "scanamo" % "1.0.0-M12-1",
      "org.scanamo" %% "scanamo-testkit" % "1.0.0-M12-1" % "test",
      "com.gu" %% "content-api-client-default" % "19.4.0",
      "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
      "com.gu" %% "pa-client" % paClientVersion,
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "org.specs2" %% "specs2-core" % specsVersion % "test",
      "org.specs2" %% "specs2-mock" % specsVersion % "test",
      "io.netty" % "netty-codec" % nettyVersion,
      "io.netty" % "netty-codec-http" % nettyVersion,
      "io.netty" % "netty-codec-http2" % nettyVersion,
      "io.netty" % "netty-common" % nettyVersion,
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13"),
      ExclusionRule("software.amazon.awssdk", "ec2")
    ),
  )

lazy val eventconsumer = lambda("eventconsumer", "eventconsumer", Some("com.gu.notifications.events.LocalRun"))
  .dependsOn(commoneventconsumer)
  .settings({
    Seq(
      description := "Consumes events produced when an app receives a notification",
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play-json" % playJsonVersion,
        "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion,
        "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
        "io.netty" % "netty-codec-http2" % nettyVersion
      ),
    )
  })

lazy val sloMonitor = lambda("slomonitor", "slomonitor", Some("com.gu.notifications.slos.SloMonitor"))
  .dependsOn(commoneventconsumer)
  .settings({
    Seq(
      description := "Monitors SLO performance for breaking news notifications",
      libraryDependencies ++= Seq(
        "com.amazonaws" % "aws-lambda-java-events" % "3.11.3",
        "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
        "io.netty" % "netty-codec" % nettyVersion,
        "io.netty" % "netty-codec-http" % nettyVersion,
        "io.netty" % "netty-codec-http2" % nettyVersion,
      ),
      Test / fork := true,
      Test / envVars := Map("STAGE" -> "TEST")
    )
  })

lazy val latestVersionOfLambdaSDK = {
  import scala.jdk.CollectionConverters._
  import com.github.dockerjava.core.DefaultDockerClientConfig
  import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
  import com.github.dockerjava.core.DockerClientImpl

  val imageName = "public.ecr.aws/lambda/java:latest"

  val dockerCfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
  val dockerHttp = new ApacheDockerHttpClient.Builder()
    .dockerHost(dockerCfg.getDockerHost())
    .sslConfig(dockerCfg.getSSLConfig())
    .build();
  val docker = DockerClientImpl.getInstance(dockerCfg, dockerHttp)

  docker.pullImageCmd(imageName).start().awaitCompletion()

  val image = docker.inspectImageCmd(imageName).exec()

  image.getRepoDigests().asScala.head
}

lazy val lambdaDockerCommands = dockerCommands := Seq(
  Cmd    ( "FROM",   latestVersionOfLambdaSDK),
  Cmd    ( "LABEL",  s"sdkBaseVersion=${latestVersionOfLambdaSDK}"),
  ExecCmd( "COPY",   "1/opt/docker/*", "${LAMBDA_TASK_ROOT}/lib/"),
  ExecCmd( "COPY",   "2/opt/docker/*", "${LAMBDA_TASK_ROOT}/lib/"),
  Cmd    ( "EXPOSE", "8080"), // this is the local lambda run time for testing
  ExecCmd( "CMD",    "com.gu.notifications.worker.ContainerLambdaTest::handleRequest"),
)

lazy val buildNumber = sys.env.get("BUILD_NUMBER").orElse(Some("DEV"))

lazy val ecrRepositorySettings =
  sys.env.get("NOTIFICATION_LAMBDA_REPOSITORY_URL") match {
    case Some(url) =>
      val Array(repo, name) = url.split("/", 2)
      Seq(
        dockerRepository := Some(repo),
        Docker / packageName := name
      )
    case None => Nil
  }

lazy val notificationworkerlambda = lambda("notificationworkerlambda", "notificationworkerlambda", Some("com.gu.notifications.worker.TopicCounterLocalRun"))
  .dependsOn(common)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(ecrRepositorySettings: _*)
  .settings(
    lambdaDockerCommands,
    dockerExposedPorts := Seq(9000), // exposed by the lambda runtime api inside the image
    dockerAlias := DockerAlias(registryHost = dockerRepository.value, username = None, name = (Docker / packageName).value, tag = buildNumber),
    libraryDependencies ++= Seq(
      "com.turo" % "pushy" % "0.13.10",
      "com.google.firebase" % "firebase-admin" % "9.1.1",
      "com.google.protobuf" % "protobuf-java" % "3.24.3",
      "com.amazonaws" % "aws-lambda-java-events" % "2.2.9",
      "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.amazonaws" % "amazon-sqs-java-messaging-lib" % "2.1.1",
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.google.oauth-client" % "google-oauth-client" % googleOAuthClient,
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    ),
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
  )

lazy val reportExtractor = lambda("reportextractor", "reportextractor", Some("com.gu.notifications.extractor.LocalRun"))
  .dependsOn(common)
  .settings(
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    )
  )
