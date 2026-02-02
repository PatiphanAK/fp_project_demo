name := "Race"
version := "1.0"
scalaVersion := "2.13.16"

// ===== Dependencies =====
lazy val Versions = new {
  val spark = "4.0.1"
  val catsEffect = "3.6.3"
  val dotenv = "3.0.0"
  val scalatest = "3.2.18"
  val hadoopVersion = "3.4.1"
}

libraryDependencies ++= Seq(
  // Spark - provided by cluster
  "org.apache.spark" %% "spark-core" % Versions.spark % Provided,
  "org.apache.spark" %% "spark-sql"  % Versions.spark % Provided,

  // Application dependencies - included in JAR
  "org.typelevel" %% "cats-effect" % Versions.catsEffect,
  "org.typelevel" %% "cats-core" % "2.13.0",
  "io.github.cdimascio" % "dotenv-java" % Versions.dotenv,
  "com.google.api-client" % "google-api-client" % "2.8.1",
  "com.google.apis" % "google-api-services-drive" % "v3-rev197-1.25.0",
  "com.crealytics" %% "spark-excel" % "3.5.1_0.20.4",
  "org.apache.hadoop" % "hadoop-aws" % Versions.hadoopVersion,
  "software.amazon.awssdk" % "bundle" % "2.26.25",
  "software.amazon.awssdk" % "url-connection-client" % "2.26.25",
  // Config libraries
  "com.typesafe" % "config" % "1.4.2",
  "com.github.pureconfig" %% "pureconfig" % "0.17.4",

  // Test
  "org.scalatest" %% "scalatest" % Versions.scalatest % Test
)

// ===== Runtime Settings =====
run / fork := true
run / outputStrategy := Some(StdoutOutput)
run / javaOptions ++= Seq(
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED",
  "-Dlog4j.configuration=log4j2.properties"
)

// ===== Assembly Settings =====
assembly / assemblyJarName := "race-app.jar"
assembly / mainClass := Some("Main")

// Merge strategy - handle conflicts
assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case PathList("META-INF", "services", _*) => MergeStrategy.concat
  case PathList("META-INF", "versions", _*) => MergeStrategy.first
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
  case PathList("META-INF", "org", "apache", "logging", "log4j", _*) => MergeStrategy.first
  case PathList("META-INF", _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}
