package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.qcresults.Encoders.checksSuiteResultsEntityEncoder
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.api.utils.Mocks
import com.github.timgent.dataflare.checkssuite.{
  CheckSuiteStatus,
  ChecksSuiteResult
}
import org.http4s.implicits.{
  http4sKleisliResponseSyntaxOptionT,
  http4sLiteralsSyntax
}
import org.http4s.{Method, Request, Status}
import zio.interop.catz.monadErrorInstance
import zio.logging.Logging
import zio.test.Assertion.equalTo
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, assert}
import zio.{RIO, ZLayer}

import java.time.Instant

object QcResultRoutesSpec extends DefaultRunnableSpec {
  def spec: ZSpec[TestEnvironment, Any] =
    suite("QcResultRoutes")(
      testM("POST /qcresults should insert QC results to ElasticSearch") {
        val elasticSearch =
          ZLayer.succeed(
            ElasticSearchConfig(List("http://127.0.0.1:9200"), "test-index")
          )
        val qcResultsRepo = elasticSearch >>> QcResultsRepo.elasticSearch
        for {
          route <- QcResultsRoutes.qcResultsRoutes.orNotFound
            .run(
              Request[RIO[QcResultsRepo with Logging, *]](
                Method.POST,
                uri"/qcresults",
              ).withEntity(
                ChecksSuiteResult(
                  CheckSuiteStatus.Success,
                  "checkSuiteDescription",
                  Seq.empty,
                  Instant.now,
                  Map.empty
                )
              )
            )
            .map(_.status)
            .provideCustomLayer(qcResultsRepo ++ Mocks.mockLogger)
        } yield assert(route)(equalTo(Status.Ok))
      }
    )
}
