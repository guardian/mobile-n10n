import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._
import play.sbt.PlayImport.specs2
import sbt.Keys.{libraryDependencies, mainClass}
import sbtassembly.AssemblyPlugin.autoImport.{assemblyJarName, assemblyMergeStrategy}
import sbtassembly.MergeStrategy
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

val projectVersion = "1.0-latest"

organization := "com.gu"
ThisBuild / scalaVersion := "2.13.8"

val compilerOptions = Seq(
  "-deprecation",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:implicitConversions"
)

ThisBuild / scalacOptions ++= compilerOptions

val playJsonVersion = "2.8.1"
val specsVersion: String = "4.5.1"
val awsSdkVersion: String = "1.12.421"
val doobieVersion: String = "0.13.4"
val catsVersion: String = "2.7.0"
val okHttpVersion: String = "4.9.3"
val paClientVersion: String = "7.0.5"
val apacheThrift: String = "0.15.0"
val jacksonDatabind: String = "2.13.3"
val jacksonCbor: String = "2.13.3"
val jacksonScalaModule: String = "2.13.3"
val simpleConfigurationVersion: String = "1.5.6"
val googleOAuthClient: String = "1.33.3"
val nettyVersion: String = "4.1.78.Final"
val slf4jVersion: String = "1.7.36"

val standardSettings = Seq[Setting[_]](
  resolvers ++= Seq(
    "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    "Guardian GitHub Snapshots" at "https://guardian.github.com/maven/repo-snapshots"
  ),
  riffRaffManifestProjectName := s"mobile-n10n:${name.value}",
  libraryDependencies ++= Seq(
    "com.github.nscala-time" %% "nscala-time" % "2.24.0",
    "com.softwaremill.macwire" %% "macros" % "2.5.7" % "provided",
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
      "com.gu" %% "mobile-logstash-encoder" % "1.1.6",
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      "io.netty" % "netty-handler" % nettyVersion,
      "io.netty" % "netty-codec" % nettyVersion,
      "io.netty" % "netty-codec-http" % nettyVersion,
      "io.netty" % "netty-codec-http2" % nettyVersion,
      "io.netty" % "netty-common" % nettyVersion,
      "org.postgresql" % "postgresql" % "42.4.1",
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
    riffRaffPackageType := (Debian / packageBin).value,
    riffRaffArtifactResources += (file(s"cdk/cdk.out/Registration-CODE.template.json"), s"registration-cfn/Registration-CODE.template.json"),
    riffRaffArtifactResources += (file(s"cdk/cdk.out/Registration-PROD.template.json"), s"registration-cfn/Registration-PROD.template.json"),
    Debian / packageName := name.value,
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
    riffRaffPackageType := (Debian / packageBin).value,
    riffRaffArtifactResources += (file(s"notification/conf/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
    Debian / packageName := name.value,
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
    riffRaffPackageType := (Debian / packageBin).value,
    riffRaffArtifactResources += (file(s"report/conf/${name.value}.yaml"), s"${name.value}-cfn/cfn.yaml"),
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
  .enablePlugins(RiffRaffArtifact, AssemblyPlugin)
  .settings(
    organization := "com.gu",
    resolvers ++= Seq(
      "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
      "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "com.gu" %% "simple-configuration-core" % simpleConfigurationVersion,
      "com.gu" %% "simple-configuration-ssm" % simpleConfigurationVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.5",
      "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
      specs2 % Test
    ),
    assemblyJarName := s"$projectName.jar",
    assembly / assemblyMergeStrategy := {
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    Test / run / fork := true,
    scalacOptions := compilerOptions,
    riffRaffPackageType := assembly.value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := s"mobile-n10n:$projectName",
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
      riffRaffArtifactResources += (file(s"schedulelambda/cfn.yaml"), s"${name.value}-cfn/cfn.yaml"),
    )
  }

lazy val football = lambda("football", "football")
  .dependsOn(apiModels  % "test->test", apiModels  % "compile->compile")
  .settings(
    resolvers += "Guardian GitHub Releases" at "https://guardian.github.com/maven/repo-releases",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.36",
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
      "org.specs2" %% "specs2-mock" % specsVersion % "test",
      "io.netty" % "netty-codec-http2" % nettyVersion
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
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1",
        "io.netty" % "netty-codec-http2" % nettyVersion
      ),
      riffRaffArtifactResources += ((baseDirectory.value / "cfn.yaml"), s"mobile-notifications-eventconsumer-cfn/cfn.yaml")
    )
  })

lazy val sloMonitor = lambda("slomonitor", "slomonitor", Some("com.gu.notifications.slos.SloMonitor"))
  .dependsOn(commoneventconsumer)
  .settings({
    Seq(
      description := "Monitors SLO performance for breaking news notifications",
      libraryDependencies ++= Seq(
        "com.amazonaws" % "aws-lambda-java-events" % "3.11.0",
        "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
        "io.netty" % "netty-codec" % nettyVersion,
      ),
      riffRaffArtifactResources +=(file("cdk/cdk.out/SloMonitor-CODE.template.json"), s"mobile-notifications-slo-monitor-cfn/SloMonitor-CODE.template.json"),
      riffRaffArtifactResources += (file("cdk/cdk.out/SloMonitor-PROD.template.json"), s"mobile-notifications-slo-monitor-cfn/SloMonitor-PROD.template.json"),
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
      "com.google.firebase" % "firebase-admin" % "9.0.0",
      "com.google.protobuf" % "protobuf-java" % "3.20.3",
      "com.amazonaws" % "aws-lambda-java-events" % "2.2.9",
      "com.amazonaws" % "aws-java-sdk-sqs" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.amazonaws" % "amazon-sqs-java-messaging-lib" % "1.1.3",
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.google.oauth-client" % "google-oauth-client" % googleOAuthClient,
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    ),
    riffRaffArtifactResources += (baseDirectory.value / "harvester-cfn.yaml", s"mobile-notifications-harvester-cfn/harvester-cfn.yaml"),
    // cdk synthesised cloudformation template
    riffRaffArtifactResources += (baseDirectory.value / "cdk" / "cdk.out" / "SenderWorkerStack-CODE.template.json", "mobile-notifications-workers-cfn/SenderWorkerStack-CODE.template.json"),
    riffRaffArtifactResources += (baseDirectory.value / "cdk" / "cdk.out" / "SenderWorkerStack-PROD.template.json", "mobile-notifications-workers-cfn/SenderWorkerStack-PROD.template.json"),
    riffRaffArtifactResources += (baseDirectory.value / "registration-cleaning-worker-cfn.yaml", s"mobile-notifications-registration-cleaning-worker-cfn/registration-cleaning-worker-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "topic-counter-cfn.yaml", s"mobile-notifications-topic-counter-cfn/topic-counter-cfn.yaml"),
    riffRaffArtifactResources += (baseDirectory.value / "expired-registration-cleaner-cfn.yaml", s"mobile-notifications-expired-registration-cleaner-cfn/expired-registration-cleaner-cfn.yaml"),
    riffRaffArtifactResources += (file("cdk/cdk.out/RegistrationsDbProxy-CODE.template.json"), s"registrations-db-proxy-cfn/RegistrationsDbProxy-CODE.template.json"),
    riffRaffArtifactResources += (file("cdk/cdk.out/RegistrationsDbProxy-PROD.template.json"), s"registrations-db-proxy-cfn/RegistrationsDbProxy-PROD.template.json")
)

lazy val ec2SenderWorker = Project("sender-worker", file("senderworker"))
  .dependsOn(common, notificationworkerlambda, commontest % "test->test")
  .enablePlugins(SystemdPlugin, RiffRaffArtifact, JavaServerAppPackaging)
  .settings(standardSettings: _*)
  .settings(
    fork := true,
    libraryDependencies ++= Seq(
      logback
    ),
    riffRaffPackageType := (Debian / packageBin).value,
    riffRaffArtifactResources += (file(s"cdk/cdk.out/SenderWorker-CODE.template.json"), s"senderworker-cfn/SenderWorker-CODE.template.json"),
    riffRaffArtifactResources += (file(s"cdk/cdk.out/SenderWorker-PROD.template.json"), s"senderworker-cfn/SenderWorker-PROD.template.json"),
    Debian / packageName := name.value,
    mainClass := Some("SenderWorker"),
    version := projectVersion
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
