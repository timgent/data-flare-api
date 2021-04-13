package com.github.timgent.dataflare.api

import com.github.timgent.dataflare.api.qcresults.QcResultsRepo
import DataflareapiServer.AppEnvironment
import zio.console.putStrLn
import zio.interop.catz.implicits._
import zio.interop.catz.taskEffectInstance
import zio.{ExitCode, URIO, ZIO}

object Main extends zio.App {
  def run(args: List[String]): URIO[zio.ZEnv, zio.ExitCode] = {
    ZIO
      .runtime[AppEnvironment]
      .flatMap { implicit runtime =>
        DataflareapiServer.stream.compile.drain.as(ExitCode.success)
      }
      .foldCauseM(
        err => putStrLn(err.prettyPrint).as(ExitCode.failure),
        _ => ZIO.succeed(ExitCode.success)
      )
      .provideCustomLayer(QcResultsRepo.elasticSearch)
  }
}
