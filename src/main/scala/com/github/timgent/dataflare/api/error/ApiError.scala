package com.github.timgent.dataflare.api.error

import zio.ZIO
import zio.logging.Logging
import zio.logging._

sealed trait ApiError {
  def message: String
  def err: Option[Throwable]
  def logErr: ZIO[Logging, Nothing, Unit] = err match {
    case Some(err) => log.throwable(message, err)
    case None      => log.error(message)
  }
}

case class QcResultsRepoErr(message: String, err: Option[Throwable])
    extends ApiError
