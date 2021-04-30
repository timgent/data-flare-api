package com.github.timgent.dataflare.api.qcresults

import cats.implicits._
import com.github.timgent.dataflare.api.error.QcResultsRepoErr
import com.github.timgent.dataflare.api.qcresults.QcRun.checkSuiteDescriptionField
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.{checksSuiteResultDecoder, checksSuiteResultEncoder}
import com.sksamuel.elastic4s.ElasticApi.{createIndex, deleteIndex, keywordField, matchAllQuery, properties, termsAgg, topHitsAgg}
import com.sksamuel.elastic4s.ElasticDsl.{CreateIndexHandler, DeleteIndexHandler, IndexHandler, SearchHandler, indexInto, search}
import com.sksamuel.elastic4s.circe.{aggReaderWithCirce, hitReaderWithCirce, indexableWithCirce}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.indexes.CreateIndexResponse
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms.TermsAggReader
import com.sksamuel.elastic4s.requests.searches.aggs.responses.metrics.TopHits
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.zio.instances._
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, Response}
import zio.{Has, IO, RLayer, Task, ZIO, ZLayer}

import scala.util.Try

object QcResultsRepo {
  type QcResultsRepo = Has[QcResultsRepo.Service]

  trait Service {
    def delQcResultsIndex: IO[QcResultsRepoErr, Unit]
    def createQcResultsIndex: IO[QcResultsRepoErr, Response[CreateIndexResponse]]
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

          override def createQcResultsIndex: IO[QcResultsRepoErr, Response[CreateIndexResponse]] =
            client
              .execute {
                createIndex(esConfig.qcResultsIndex).mapping(
                  properties(
                    keywordField(
                      "checkSuiteDescription"
                    ) // other fields can be automatically created. This one must be keyword to enable aggregations
                  )
                )
              }
              .mapError(e => QcResultsRepoErr(s"Could not create index ${esConfig.qcResultsIndex}", Some(e)))

          override def delQcResultsIndex: IO[QcResultsRepoErr, Unit] =
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

          override def getLatestQcs: IO[QcResultsRepoErr, List[QcRun]] = {
            val checkSuiteDescriptions = "checkSuiteDescriptions"
            for {
              res <- client.execute(
                search(esConfig.qcResultsIndex) query matchAllQuery size 0 aggregations termsAgg(
                  checkSuiteDescriptions,
                  checkSuiteDescriptionField
                ).subAggregations(
                  topHitsAgg("latestQcRun").size(1).sortBy(List(FieldSort("timestamp").order(SortOrder.Desc)))
                )
              )
              agg = res.result.aggregations.result[Terms](checkSuiteDescriptions)
              tophits = agg.buckets.map(_.result[TopHits]("latestQcRun"))
              qcRuns <- IO.fromTry(tophits.flatMap(_.hits.map(_.safeTo[QcRun])).toList.traverse(identity))
            } yield qcRuns
          }.mapError(e => QcResultsRepoErr("Could not get latest QCs", Some(e)))

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
