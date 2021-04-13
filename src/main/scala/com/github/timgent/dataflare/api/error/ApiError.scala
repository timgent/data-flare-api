package com.github.timgent.dataflare.api.error

sealed trait ApiError {
  def message: String
  def err: Option[Throwable]
}

case class QcResultsRepoErr(message: String, err: Option[Throwable])
    extends ApiError
