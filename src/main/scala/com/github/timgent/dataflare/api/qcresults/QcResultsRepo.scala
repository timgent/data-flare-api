package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.error.QcResultsRepoErr
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import zio.{Has, IO, ULayer, ZIO, ZLayer}

object QcResultsRepo {
  type QcResultsRepo = Has[QcResultsRepo.Service]

  trait Service {
    def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]]
    def saveCheckSuiteResult(
      checksSuiteResult: ChecksSuiteResult
    ): IO[QcResultsRepoErr, Unit]
  }

  val elasticSearch: ULayer[Has[Service]] = ZLayer.succeed(new Service {
    override def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]] =
      IO.fail(QcResultsRepoErr("Oh no!!", None)) // TODO: Implement me
    override def saveCheckSuiteResult(
      checksSuiteResult: ChecksSuiteResult
    ): IO[QcResultsRepoErr, Unit] =
      IO.fail(QcResultsRepoErr("Or no!!", None)) // TODO: Implement me
  })

  def getLatestQcs: ZIO[QcResultsRepo, QcResultsRepoErr, List[QcRun]] =
    ZIO.accessM(_.get.getLatestQcs)

  def saveChecksSuiteResult(
    checksSuiteResult: ChecksSuiteResult
  ): ZIO[QcResultsRepo, QcResultsRepoErr, Unit] =
    ZIO.accessM(_.get.saveCheckSuiteResult(checksSuiteResult))
}
