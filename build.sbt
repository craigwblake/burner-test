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
