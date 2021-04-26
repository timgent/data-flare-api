package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.error.QcResultsRepoErr
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.{checksSuiteResultDecoder, checksSuiteResultEncoder}
import com.sksamuel.elastic4s.ElasticApi.{deleteIndex, matchAllQuery}
import com.sksamuel.elastic4s.ElasticDsl.{DeleteIndexHandler, IndexHandler, SearchHandler, indexInto, search}
import com.sksamuel.elastic4s.circe.{hitReaderWithCirce, indexableWithCirce}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.zio.instances._
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import zio.{Has, IO, RLayer, Task, ZIO, ZLayer}

import scala.util.Try

object QcResultsRepo {
  type QcResultsRepo = Has[QcResultsRepo.Service]

  trait Service {
    def delIndex: IO[QcResultsRepoErr, Unit]
    def getAllCheckSuiteResults: IO[QcResultsRepoErr, List[ChecksSuiteResult]]
    def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]]
    def saveCheckSuiteResult(
        checksSuiteResult: ChecksSuiteResult
    ): IO[QcResultsRepoErr, Unit]
  }

  val elasticSearch: RLayer[Has[ElasticSearchConfig], QcResultsRepo] =
    ZLayer.fromServiceM { esConfig =>
      for {
        client <- esConfig.getClient
        svc = new Service {

          override def delIndex: IO[QcResultsRepoErr, Unit] =
            client
              .execute { deleteIndex(esConfig.qcResultsIndex) }
              .map(_ => ())
              .mapError(t => QcResultsRepoErr("Could not delete index", Some(t)))

          override def getAllCheckSuiteResults: IO[QcResultsRepoErr, List[ChecksSuiteResult]] =
            for {
              res <-
                client
                  .execute(search(esConfig.qcResultsIndex) query matchAllQuery)
                  .mapError(e => QcResultsRepoErr("Couldn't get all CheckSuiteResults", Some(e)))
              checkSuiteResults = res.result.hits.hits.map(_.to[ChecksSuiteResult]).toList
            } yield checkSuiteResults

          override def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]] =
            IO.fail(QcResultsRepoErr("Oh no!!", None)) // TODO: Implement me

          override def saveCheckSuiteResult(
              checksSuiteResult: ChecksSuiteResult
          ): IO[QcResultsRepoErr, Unit] = {
            val index = esConfig.qcResultsIndex
            (for {
              res <-
                client
                  .execute(indexInto(index).doc(checksSuiteResult))
                  .map(_ => ())
            } yield res).mapError(e =>
              QcResultsRepoErr(
                s"Could not save doc $checksSuiteResult to index",
                Some(e)
              )
            )
          }
        }
      } yield svc
    }

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
