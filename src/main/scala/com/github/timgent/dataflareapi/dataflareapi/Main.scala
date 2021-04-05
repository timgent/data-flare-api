package com.github.timgent.dataflareapi.dataflareapi

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    DataflareapiServer.stream[IO].compile.drain.as(ExitCode.Success)
  }
}
