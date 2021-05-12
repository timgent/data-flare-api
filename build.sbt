val Http4sVersion = "0.21.16"
val CirceVersion = "0.13.0"
val MunitVersion = "0.7.20"
val LogbackVersion = "1.2.3"
val MunitCatsEffectVersion = "0.13.0"
val zioVersion = "1.0.5"

val elastic4sVersion = "7.12.0"

def chooseIfLenientCompile(compileOptions: Seq[String]): Seq[String] = {
  val isProduction = sys.env.getOrElse("DFA_PRODUCTION", "false").toBoolean
  if (isProduction) {
    compileOptions
  } else {
    compileOptions.filterNot(
      productionOnlyCompileOptions
    )
  }
}

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
      "com.github.timgent" %% "data-flare" % "2.4.5_0.1.14",
      "com.sksamuel.elastic4s" % "elastic4s-core_2.12" % elastic4sVersion,
      "com.sksamuel.elastic4s" % "elastic4s-client-esjava_2.12" % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-effect-zio" % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test",
      "dev.zio" %% "zio-config" % "1.0.4",
      "dev.zio" %% "zio-config-magnolia" % "1.0.4"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework"),
    scalacOptions ~= (chooseIfLenientCompile)
  )

val productionOnlyCompileOptions = Set(
  "-deprecation",
  "-explaintypes",
  "-explain-types",
  "-explain",
  "-feature",
  "-language:existentials",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xcheckinit",
  "-Xfatal-warnings",
  "-Xlint",
  "-Xlint:adapted-args",
  "-Xlint:by-name-right-associative",
  "-Xlint:constant",
  "-Xlint:delayedinit-select",
  "-Xlint:deprecation",
  "-Xlint:doc-detached",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:nullary-override",
  "-Xlint:nullary-unit",
  "-Xlint:option-implicit",
  "-Xlint:package-object-classes",
  "-Xlint:poly-implicit-overload",
  "-Xlint:private-shadow",
  "-Xlint:stars-align",
  "-Xlint:strict-unsealed-patmat",
  "-Xlint:unsound-match",
  "-Xlint:-byname-implicit",
  "-Wunused:nowarn",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Wdead-code",
  "-Ywarn-extra-implicit",
  "-Wextra-implicit",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused",
  "-Ywarn-unused-import",
  "-Ywarn-unused:implicits",
  "-Wunused:implicits",
  "-Wunused:explicits",
  "-Ywarn-unused:imports",
  "-Wunused:imports",
  "-Ywarn-unused:locals",
  "-Wunused:locals",
  "-Ywarn-unused:params",
  "-Wunused:params",
  "-Ywarn-unused:patvars",
  "-Wunused:patvars",
  "-Ywarn-unused:privates",
  "-Wunused:privates",
  "-Ywarn-value-discard",
  "-Wvalue-discard"
)
