-injars "/Users/alex_dufournet/code/mobile-n10n/notificationworkerlambda/target/scala-2.12/notificationworkerlambda.jar"
-outjars "/Users/alex_dufournet/code/mobile-n10n/notificationworkerlambda/target/scala-2.12/proguard/notificationworkerlambda_2.12-1.0.6-SNAPSHOT.jar"
-dontpreverify
-dontoptimize
-dontnote
-dontwarn
-ignorewarnings
-dontobfuscate
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-verbose

-keep public class com.gu.notifications.worker.Harvester



