package com.github.timgent.dataflare.api

import cats.effect.{ConcurrentEffect, Timer}
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.api.qcresults.QcResultsRoutes
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig, Logger}
import zio.RIO
import zio.logging.Logging

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

object DataflareapiServer {
  type AppEnvironment = QcResultsRepo with Logging
  type AppTask[A] = RIO[AppEnvironment, A]
  def stream(implicit
      T: Timer[AppTask],
      concurrentEffect: ConcurrentEffect[AppTask]
  ): Stream[AppTask, Nothing] = {
    val cors = CORSConfig(
      anyOrigin = true, // TODO: Allow allowed origins to be specified in config
      anyMethod = true,
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )

    val httpApp = CORS(QcResultsRoutes.qcResultsRoutes.orNotFound, cors)

    // With Middlewares in place for logging
    val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)
    for {
      exitCode <- BlazeServerBuilder[AppTask](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
