import sbt._

object Dependencies {

  val scalatest    = "org.scalatest"              %% "scalatest"     % "3.0.5" % Test
  val cats         = "org.typelevel"              %% "cats-core"     % "1.0.1"
  val scalaChart   = "com.github.wookietreiber"   %% "scala-chart"   % "0.5.1"
  val scalaLogging = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
  )
}
