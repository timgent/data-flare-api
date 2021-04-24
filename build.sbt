val Http4sVersion = "0.21.16"
val CirceVersion = "0.13.0"
val MunitVersion = "0.7.20"
val LogbackVersion = "1.2.3"
val MunitCatsEffectVersion = "0.13.0"
val zioVersion = "1.0.5"

lazy val root = (project in file("."))
  .settings(
    organization := "com.github.timgent.dataflareapi",
    name := "data-flare-api",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.10",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "org.scalameta" %% "munit" % MunitVersion % Test,
      "org.typelevel" %% "munit-cats-effect-2" % MunitCatsEffectVersion % Test,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.scalameta" %% "svm-subs" % "20.2.0",
      "dev.zio" %% "zio-interop-cats" % "2.4.0.0",
      "dev.zio" %% "zio-logging" % "0.5.8",
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
      "com.github.timgent" %% "data-flare" % "2.4.5_0.1.14-SNAPSHOT", // TODO: Publish this version!!
      "com.sksamuel.elastic4s" % "elastic4s-core_2.12" % "7.12.0",
      "com.sksamuel.elastic4s" % "elastic4s-client-esjava_2.12" % "7.12.0",
      "com.sksamuel.elastic4s" %% "elastic4s-effect-zio" % "7.12.0",
      "dev.zio" %% "zio-config" % "1.0.4",
      "dev.zio" %% "zio-config-magnolia" % "1.0.4"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework"),
    scalacOptions ~= (_.filterNot(Set("-Wdead-code")))
  )
