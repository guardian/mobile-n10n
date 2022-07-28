# Reducing Lambda Cold Start Times

The hypothesis we tested was that by:
- minimising lambda dependencies, and
- reverting to deploying lambdas with a .zip archive, and
- using the latest aws sdk (v2), and
- compiling for the latest java version (java 11 corretto), and
- enabling only level 1 tiered compilation

then the result would be reduced lambda start up and execution times.

## Summary

Splitting out the ios (and android) sender lambdas reduced the size of the assembled code to ~80MB.

Given the reduced size of the assembled code we could revert to deploying the lambda with a zip archive (as opposed to a container image). This resulted in a 0.5-1s saving during cold starts.

Migrating to the latest version of the aws java sdk (v2) resulted in small gains in execution time (~0.5s).

Compiling for java 11 and specifying java 11 (corretto) as the runtime for our experimental lambda resulted in no noticeable improvement in cold start or execution times.

The changes weren't simple to test and were done quickly and roughly to get a sense for any potential improvements. Given the potential effort required to split up the existing scala lambda vs the potential gain we think the better option would be to migrate the lambdas to typescript (or another more performant language).

## Test Results

We experimented with the ios and android sender lambdas. We manually created a new lambda function for testing purposes only (once a lambda has been created using container images it cannot be converted to deploy using a .zip archive).

### Deploying Lambdas with a .zip archive

In order to deploy a lambda with a .zip archive we needed to reduce the artifact size.

The sender lambda code was extracted from the `notificationworkerlambda` module. Initially the experimental `workerlambda` relied on `common` but the resulting .zip exceeded the size limit accepted by lambda (250MB).


To eliminate the dependency on `common` some classes were copied across (along with some necessary dependencies) and the new project definition could be defined:

```scala
lazy val workerlambda = lambda("workerlambda", "workerlambda", None)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.typesafe.play" %% "play-json-joda" % playJsonVersion,
      "ai.x" %% "play-json-extensions" % "0.42.0",
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "2.2.8",
      "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
      "com.google.firebase" % "firebase-admin" % "8.1.0",
      "com.turo" % "pushy" % "0.13.10",
      "software.amazon.awssdk" % "sqs" % "2.17.239",
      "org.typelevel" %% "cats-effect" % "2.5.1",
      "co.fs2" %% "fs2-core" % "2.5.6",
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    ),
  )
```

NB: the `lambda` project has only minimal dependencies for logging, aws-lambda-java-core and aws simple configuration.

The resulting .jar from this project was 81.4MB and was uploaded to s3. It was then specified as a source for our manually created lambda.

We created a test event to successfully trigger the lambda. The results below show the difference in timings:

| |Init Duration (ms)|Duration (ms)|
|:----|:----|:----|
|ECR|4777|2893|
|ZIP|4015|2665|

We can see a marginal increase, but perhaps not worth the engineering effort to achieve this.

### Migrating to AWS SDK v2

We migrated the experiment sender lambda to use version 2 of the java aws sdk and it was [indicated](https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/) that the latest library would bring about performance improvements.

After migrating to version 2 (where we could, because no new version existed for `aws-lambda-java-events`) this was the resulting project definition:

```scala
lazy val workerlambda = lambda("workerlambda", "workerlambda", None)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion,
      "com.typesafe.play" %% "play-json-joda" % playJsonVersion,
      "ai.x" %% "play-json-extensions" % "0.42.0",
      "com.squareup.okhttp3" % "okhttp" % okHttpVersion,
      "software.amazon.awssdk" % "lambda" % "2.17.239",
      "com.amazonaws" % "aws-lambda-java-events" % "2.2.8",
      "software.amazon.awssdk" % "cloudwatch" % "2.17.239",
      "com.google.firebase" % "firebase-admin" % "8.1.0",
      "com.turo" % "pushy" % "0.13.10",
      "software.amazon.awssdk" % "sqs" % "2.17.239",
      "org.typelevel" %% "cats-effect" % "2.5.1",
      "co.fs2" %% "fs2-core" % "2.5.6",
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.play", "play-ahc-ws_2.13")
    )
  )
```

The change slightly increased the artifact size (to 83.3MB) but it could still be specified as the source for our lambda. The results below show the difference in timings:

| |Init Duration (ms)|Duration (ms)|
|:----|:----|:----|
|AWS SDK v1|4015|2665|
|AWS SDK v2|3575|2488|

It might be a good health task to migrate our `notificationworkerlambda` to the aws sdk v2 (without also breaking out the individual lambdas) as we may get a small performance benefit as a result. 

### Upgrading to Java 11

The build.sbt for mobile-n10n does not define any jvm options. We defined java 11 on our local machines and compile + assembled the experimental lambda, as well as updating the runtime defined for the function in the aws console.

When testing we did not observe any noticeable difference in the cold start or execution time.

### Restricting Tiered Compilation Setting

There is interesting [information](https://aws.amazon.com/blogs/compute/increasing-performance-of-java-aws-lambda-functions-using-tiered-compilation/) about how to reduce the cold start times of jvm-based lambdas. The suggestion is to use tiered compilation to reduce the cold start time of the lambda.

By only allowing 'level 1' compilation (no profiling) it reduces the amount of profiling and therefore the time before code can be executed.

To implement this we needed a lambda 'layer' to define this setting. We couldn't apply a 'layer' because of size restrictions on lambdas (the size restriction applied to the unzipped package size).

As a result, we couldn't easily test whether this setting had a measurable impact on cold start time.