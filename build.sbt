import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.reflect.io.File

import com.typesafe.sbt.packager.docker.DockerVersion
import wartremover.WartRemover.autoImport.Wart

name := """somnus"""
organization := "ch.chronobiology"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.1"

scalacOptions ++= Seq("-Ywarn-unused")

resolvers += Resolver.jcenterRepo

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

addCommandAlias(
  "test-ci",
  """;set Test/javaOptions --= Seq("-Dconfig.file=conf/application.test.conf")""" +
    """;set Test/javaOptions ++= Seq("-Dconfig.file=conf/application.test-ci.conf")""" +
    ";test"
)

libraryDependencies += "com.h2database" % "h2" % "1.4.200"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2"
)
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.20"

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "7.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "7.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "7.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "7.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "7.0.0" % "test"
)

libraryDependencies += "net.codingwell" %% "scala-guice" % "4.2.6"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.30"

libraryDependencies += "com.opencsv" % "opencsv" % "4.1"

libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.0"

libraryDependencies += "org.apache.commons" % "commons-collections4" % "4.4"

wartremoverErrors in (Compile, compile) ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.Throw,
  Wart.Null,
  Wart.StringPlusAny,
  Wart.Any // FIXME: Should probably be enabled, but too many (false?) positives
)
wartremoverErrors in Test ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.OptionPartial,
  Wart.Null,
  Wart.Throw,
  Wart.Any,
  Wart.TraversableOps,
  Wart.Var,
  Wart.StringPlusAny
)
wartremoverExcluded ++= routes.in(Compile).value

// add scalastyle to compile task
lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
compileScalastyle := scalastyle.in(Compile).toTask("").value
(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

// add scalastyle to test task
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
testScalastyle := scalastyle.in(Test).toTask("").value
(test in Test) := ((test in Test) dependsOn testScalastyle).value

(scalastyleConfig in Test) := baseDirectory.value / "scalastyle-test-config.xml"

// create suffixed version string
val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
val timestamp = now.toString.replace(":", "-")
val versionSuffix = "_" + timestamp

// append suffixed version string to (stage) config before compile
// FIXME: A lot of shared boilerplate with docker build version inlining below
val appendVersionToConf = taskKey[Unit]("Append (suffixed) version to application.conf for stage-based builds")
appendVersionToConf := {
  val suffixedVersion = (ThisBuild / version).value + versionSuffix
  (ThisBuild / version) := suffixedVersion

  val stageLocation = (Universal / stagingDirectory).value
  val topLevelLocation = (Compile / classDirectory).value

  // FIXME: This probably has no effect
  val buildConfigFile = File(s"$topLevelLocation/application.conf")
  if (buildConfigFile.exists)
    buildConfigFile.appendAll(s"\napp.version=$suffixedVersion\n")

  val stageConfigFile = File(s"$stageLocation/conf/application.conf")
  if (stageConfigFile.exists)
    stageConfigFile.appendAll(s"\napp.version=$suffixedVersion\n")
}
(Compile / compile) := ((Compile / compile) dependsOn appendVersionToConf).value


// ~ Integration testing ~

addCommandAlias(
  "testServe",
  "testProd -Dplay.http.secret.key='dummy-secret' " +
    "-Dconfig.file=conf/application.test.conf -DtestServe=true"
)


// ~ Docker publication ~

dockerVersion := Some(DockerVersion(19, 3, 11, Some("ce")))
dockerEntrypoint := Seq("bin/somnus", "-Dconfig.file=conf/application.dist.conf")
dockerExposedPorts := Seq(9000)

dockerRepository := Some("docker.pkg.github.com/upkbs-chronobiology")
dockerUsername := Some("somnus")

(version in Docker) := (version in Docker).value + versionSuffix

// FIXME: A lot of shared boilerplate with base build version inlining above
val appendVersionToConfDocker = taskKey[Unit]("Append (suffixed) version to application.conf for docker builds")
appendVersionToConfDocker := {
  val versionString = (Docker / version).value

  val stageLocation = (Docker / stage).value
  val configFile = File(s"$stageLocation/opt/docker/conf/application.conf")
  if (configFile.exists)
    configFile.appendAll(s"\napp.version=$versionString\n")
}
(Docker / publishLocal) := ((Docker / publishLocal) dependsOn appendVersionToConfDocker).value

// Database password should be provided through a command line flag (-Dslick.dbs.default.db.password) or environment
// variable (DB_PASSWORD).

// Application secret should be provided through a command line flag (-Dplay.http.secret.key) or environment variable
// (APPLICATION_SECRET).


// ~ Releasing ~

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepTask(publishLocal in Docker),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
