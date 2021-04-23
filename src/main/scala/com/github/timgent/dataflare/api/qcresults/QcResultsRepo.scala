package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.error.QcResultsRepoErr
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.checksSuiteResultEncoder
import com.sksamuel.elastic4s.ElasticDsl.{IndexHandler, indexInto}
import com.sksamuel.elastic4s.circe.indexableWithCirce
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.zio.instances._
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import zio.{Has, IO, Task, URLayer, ZIO, ZLayer}

import scala.util.Try

object QcResultsRepo {
  type QcResultsRepo = Has[QcResultsRepo.Service]

  trait Service {
    def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]]
    def saveCheckSuiteResult(
      checksSuiteResult: ChecksSuiteResult
    ): IO[QcResultsRepoErr, Unit]
  }

  val elasticSearch: URLayer[Has[ElasticSearchConfig], QcResultsRepo] =
    ZLayer.fromService(
      esConfig =>
        new Service {
          override def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]] =
            IO.fail(QcResultsRepoErr("Oh no!!", None)) // TODO: Implement me
          override def saveCheckSuiteResult(
            checksSuiteResult: ChecksSuiteResult
          ): IO[QcResultsRepoErr, Unit] = {
            (for {
              client <- esConfig.getClient
              index = esConfig.qcResultsIndex
              res <- client
                .execute(indexInto(index).doc(checksSuiteResult))
                .map(_ => ())
            } yield res).mapError(
              e =>
                QcResultsRepoErr(
                  s"Could not save doc $checksSuiteResult to index",
                  Some(e)
              )
            )
          }
      }
    )

  def getLatestQcs: ZIO[QcResultsRepo, QcResultsRepoErr, List[QcRun]] =
    ZIO.accessM(_.get.getLatestQcs)

  def saveChecksSuiteResult(
    checksSuiteResult: ChecksSuiteResult
  ): ZIO[QcResultsRepo, QcResultsRepoErr, Unit] =
    ZIO.accessM[QcResultsRepo](_.get.saveCheckSuiteResult(checksSuiteResult))
}

case class ElasticSearchConfig(hosts: List[String], qcResultsIndex: String) {
  def getClient: Task[ElasticClient] =
    Task.fromTry(Try {
      val hostList = hosts.reduceLeft(_ + "," + _)
      val client: ElasticClient =
        ElasticClient(JavaClient(ElasticProperties(hostList)))
      client
    })
}
