package com.github.timgent.dataflare.api

import cats.effect.{ConcurrentEffect, Timer}
import com.github.timgent.dataflare.api.qcresults.QcResultsRoutes
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import zio.RIO

import scala.concurrent.ExecutionContext.global

object DataflareapiServer {
  type AppEnvironment = QcResultsRepo
  type AppTask[A] = RIO[AppEnvironment, A]
  def stream(
    implicit T: Timer[AppTask],
    concurrentEffect: ConcurrentEffect[AppTask]
  ): Stream[AppTask, Nothing] = {
    // Combine Service Routes into an HttpApp.
    // Can also be done via a Router if you
    // want to extract a segments not checked
    // in the underlying routes.
    val httpApp = (
      QcResultsRoutes.getLatestResultsRoute
    ).orNotFound

    // With Middlewares in place
    val finalHttpApp = Logger.httpApp(true, true)(httpApp)
    for {
      exitCode <- BlazeServerBuilder[AppTask](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
