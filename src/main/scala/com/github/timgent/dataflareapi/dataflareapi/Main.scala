package com.github.timgent.dataflareapi.dataflareapi

import zio.console.putStrLn
import zio.interop.catz.implicits._
import zio.interop.catz.taskEffectInstance
import zio.{ExitCode, Task, URIO, ZEnv, ZIO}

object Main extends zio.App {
  def run(args: List[String]): URIO[zio.ZEnv, zio.ExitCode] = {
    ZIO
      .runtime[ZEnv]
      .flatMap { implicit runtime =>
        DataflareapiServer.stream[Task].compile.drain.as(ExitCode.success)
      }.foldCauseM(
      err => putStrLn(err.prettyPrint).as(ExitCode.failure),
      _ => ZIO.succeed(ExitCode.success)
    )
  }
}
