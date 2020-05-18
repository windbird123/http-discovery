name := "http-discovery"
version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.12"
//scalaVersion := "2.12.10"

scalacOptions := Seq(
  "-encoding",
  "UTF-8",                 // source files are in UTF-8
  "-deprecation",          // warn about use of deprecated APIs
  "-unchecked",            // warn about unchecked type parameters
  "-feature",              // warn about misused language features
  "-language:higherKinds", // allow higher kinded types without `import scala.language.higherKinds`
  "-Ypartial-unification", // allow the compiler to unify type constructors of different arities
  "-language:implicitConversions"
)

val zioVersion = "1.0.0-RC19"

libraryDependencies ++= Seq(
  "dev.zio"                    %% "zio"            % zioVersion,
  "dev.zio"                    %% "zio-streams"    % zioVersion,
  "dev.zio"                    %% "zio-test"       % zioVersion % "test",
  "dev.zio"                    %% "zio-test-sbt"   % zioVersion % "test",
  "org.typelevel"              %% "simulacrum"     % "1.0.0",
  "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2",
  "org.slf4j"                  % "jcl-over-slf4j"  % "1.7.26",
  "ch.qos.logback"             % "logback-classic" % "1.2.3",
  "org.scalaj"                 %% "scalaj-http"    % "2.4.2",
  "com.typesafe.play"          %% "play-json"      % "2.6.6"
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")
addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)
