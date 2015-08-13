name := "add2cal"

version := "1.0.0"

scalaVersion := """2.11.6"""

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.4.0",
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "2.3.1",
  "javax.mail" % "mail" % "1.4.5",
  "com.typesafe.slick" %% "slick" % "3.0.1",
  "com.zaxxer" % "HikariCP" % "2.3.8",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "org.postgresql" % "postgresql" % "9.4-1200-jdbc41",
  "org.apache.commons" % "commons-email" % "1.3.3",
  "mysql" % "mysql-connector-java" % "5.1.35"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)
