name := "play-streak"

version := "1.0.0"

scalaVersion := """2.11.6"""

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "2.3.1"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
