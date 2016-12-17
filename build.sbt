enablePlugins(PlayScala)

organization := "cwb"
name := "test"
version := "1.0"

scalaVersion := "2.11.8"

scalacOptions in Compile := Vector(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen")

libraryDependencies += ws
libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.8.+" % "test")
libraryDependencies ++= Seq("org.specs2" %% "specs2-mock" % "3.8.+" % "test")
libraryDependencies ++= Seq("org.specs2" %% "specs2-junit" % "3.8.+" % "test")
