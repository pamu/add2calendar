name := "add2cal"

version := "1.0.0"

scalaVersion := """2.11.6"""

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.4.0",
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "2.3.1",
  "javax.mail" % "mail" % "1.4.5"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
