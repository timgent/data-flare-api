package com.github.timgent.dataflare.api

import com.github.timgent.dataflare.api.DataflareapiServer.AppEnvironment
import com.github.timgent.dataflare.api.qcresults.{
  ElasticSearchConfig,
  QcResultsRepo
}
import zio.config.magnolia.Descriptor._
import zio.console.putStrLn
import zio.interop.catz.implicits._
import zio.interop.catz.taskEffectInstance
import zio.logging.{LogFormat, LogLevel, Logging}
import zio.{ExitCode, IO, URIO, ZIO, ZLayer, system}
import zio.config._

case class Test(x: List[String])

object Main extends zio.App {
  def run(args: List[String]): URIO[zio.ZEnv, zio.ExitCode] = {
    val logging = Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("data-flare-api")
    val config: ZIO[system.System, ReadError[String], ElasticSearchConfig] =
      for {
        ses <- ConfigSource.fromSystemEnv
        d <- ZIO.fromEither(read(descriptor[ElasticSearchConfig].from(ses)))
      } yield d
    val configZlayer = ZLayer.fromEffect(config)
    val program = ZIO
      .runtime[AppEnvironment]
      .flatMap { implicit runtime =>
        DataflareapiServer.stream.compile.drain.as(ExitCode.success)
      }
      .foldCauseM(
        err => putStrLn(err.prettyPrint).as(ExitCode.failure),
        _ => ZIO.succeed(ExitCode.success)
      )
      .provideCustomLayer[ReadError[String], AppEnvironment](
        (configZlayer >>> QcResultsRepo.elasticSearch) ++ logging
      )

    program.foldM(
      err =>
        putStrLn(s"Execution failed with: $err") *> IO.succeed(
          ExitCode.failure
      ),
      _ => IO.succeed(ExitCode.success)
    )
  }
}
