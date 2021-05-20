package com.github.timgent.dataflare.api.qcresults

import cats.implicits._
import com.github.timgent.dataflare.api.error.QcResultsRepoErr
import com.github.timgent.dataflare.api.qcresults.QcRun.checkSuiteDescriptionField
import com.github.timgent.dataflare.api.utils.WithId
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
    def getLatestQcs: IO[QcResultsRepoErr, List[WithId[QcRun]]]
    def getQcsByDescription(description: String): IO[QcResultsRepoErr, List[WithId[QcRun]]]
    def saveCheckSuiteResult(
        checksSuiteResult: ChecksSuiteResult,
        id: Option[String] = None
    ): IO[QcResultsRepoErr, Unit]
    def saveCheckSuiteResultWithId(
        checksSuiteResultWithId: WithId[ChecksSuiteResult]
    ): IO[QcResultsRepoErr, Unit] = saveCheckSuiteResult(checksSuiteResultWithId.value, Some(checksSuiteResultWithId.id))
  }

  val elasticSearch: RLayer[Has[ElasticSearchConfig], QcResultsRepo] =
    ZLayer.fromServiceM { esConfig =>
      for {
        client <- esConfig.getClient
        svc = new Service {

          override def getQcsByDescription(description: String): IO[QcResultsRepoErr, List[WithId[QcRun]]] = {
            for {
              res <-
                client
                  .execute(
                    search(esConfig.qcResultsIndex) matchQuery (QcRun.checkSuiteDescriptionField, description)
                  )
                  .mapError(e => QcResultsRepoErr(s"Couldn't get QcRuns for ${QcRun.checkSuiteDescriptionField} = '$description'", Some(e)))
              checkSuiteResults = res.result.hits.hits.map(hit => WithId(hit.id, hit.to[QcRun])).toList
            } yield checkSuiteResults
          }

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

          override def getLatestQcs: IO[QcResultsRepoErr, List[WithId[QcRun]]] = {
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
              agg <- ZIO.effect(res.result.aggregations.result[Terms](checkSuiteDescriptions))
              tophits = agg.buckets.map(_.result[TopHits]("latestQcRun"))
              qcRuns <- IO.fromTry(tophits.flatMap(_.hits.map(hit => hit.safeTo[QcRun].map(WithId(hit.id, _)))).toList.traverse(identity))
            } yield qcRuns
          }.mapError(e => QcResultsRepoErr("Could not get latest QCs", Some(e)))

          override def saveCheckSuiteResult(checksSuiteResult: ChecksSuiteResult, id: Option[String]): IO[QcResultsRepoErr, Unit] = {
            val queryResult = for {
              res <- client.execute(indexInto(esConfig.qcResultsIndex).copy(id = id).doc(checksSuiteResult)).map(_ => ())
            } yield res
            queryResult.mapError(e => QcResultsRepoErr(s"Could not save doc $checksSuiteResult to index", Some(e)))
          }
        }
      } yield svc
    }

  def getLatestQcs: ZIO[QcResultsRepo, QcResultsRepoErr, List[WithId[QcRun]]] =
    ZIO.accessM(_.get.getLatestQcs)

  def getQcsByDescription(description: String): ZIO[QcResultsRepo, QcResultsRepoErr, List[WithId[QcRun]]] =
    ZIO.accessM(_.get.getQcsByDescription(description))

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
