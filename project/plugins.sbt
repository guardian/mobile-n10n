resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0")

addSbtPlugin("com.gu" % "sbt-riffraff-artifact" % "0.9.9")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("com.localytics" % "sbt-dynamodb" % "1.5.5")

libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.7"