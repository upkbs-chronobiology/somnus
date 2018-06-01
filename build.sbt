import scala.io.StdIn.readLine
import scala.io.Source
import scala.tools.nsc.io.File

name := """somnus"""
organization := "ch.chronobiology"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.3"

scalacOptions ++= Seq("-Ywarn-unused")

resolvers += Resolver.jcenterRepo

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
javaOptions in Test += "-Dconfig.file=conf/application.test.conf"

libraryDependencies += "com.h2database" % "h2" % "1.4.197"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "3.0.3",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3"
)

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "5.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "5.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "5.0.0" % "test"
)

libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.1"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.18"

libraryDependencies += "com.opencsv" % "opencsv" % "4.1"

wartremoverErrors in(Compile, compile) ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.Throw,
  Wart.Null
)
wartremoverErrors in Test ++= Warts.unsafe diff List(
  Wart.NonUnitStatements,
  Wart.DefaultArguments,
  Wart.OptionPartial,
  Wart.Null,
  Wart.Throw,
  Wart.Any,
  Wart.TraversableOps,
  Wart.Var
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


// ~ Docker publication ~

dockerEntrypoint := Seq("bin/somnus", "-Dconfig.file=conf/application.dist.conf")
dockerExposedPorts := Seq(9000)

val prepConfigForDocker = taskKey[Unit]("Append db password to target dist config file")
prepConfigForDocker := {
  val stageLocation = (Docker / stage).value

  val configFile = File(s"$stageLocation/opt/docker/conf/application.dist.conf")

  val dbPseudoDomain = "somnus-db"
  val mappedLines = Source.fromFile(configFile.path).getLines()
    .map(_.replaceAll("tcp://localhost", s"tcp://$dbPseudoDomain")).toSeq

  // TODO: What if PW is already present (e.g. from previous build, without clean since)?
  val dbPassword = readLine("Enter database user password: ")
  val passwordLine = s"""slick.dbs.default.db.password="$dbPassword""""

  val applicationSecret = readLine("Enter application secret key: ")
  val secretLine = s"""play.http.secret.key="$applicationSecret""""

  configFile.writeAll((mappedLines ++ Seq(passwordLine, secretLine)).mkString("\n"))
}

(Docker / publishLocal) := ((Docker / publishLocal) dependsOn prepConfigForDocker).value


// ~ Releasing ~

import ReleaseTransformations._

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
