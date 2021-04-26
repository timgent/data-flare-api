package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.qcresults.Encoders.checksSuiteResultsEntityEncoder
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.api.utils.Mocks
import com.github.timgent.dataflare.checkssuite.{CheckSuiteStatus, ChecksSuiteResult}
import com.sksamuel.elastic4s.testkit.DockerTests
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import org.http4s.{Method, Request, Status}
import zio.interop.catz.monadErrorInstance
import zio.logging.Logging
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Cause, RIO, ZIO, ZLayer}

import java.time.{Duration, Instant}

object QcResultRoutesSpec extends DefaultRunnableSpec with DockerTests {

  val testIndex = "test-index"
  def testWithCleanIndexM[R, E](testName: String)(assertion: => ZIO[R, E, TestResult])(implicit loc: SourceLocation) = {
    testM(testName) {
      for {
        repo <- ZIO.access[QcResultsRepo](_.get)
        _ <- repo.delIndex
        a <- assertion
      } yield a
    }
  }

  def spec = {
    val elasticSearch = ZLayer.succeed(ElasticSearchConfig(List("http://127.0.0.1:9200"), testIndex))
    val qcResultsRepo: ZLayer[Any, TestFailure.Runtime[Throwable], QcResultsRepo] =
      (elasticSearch >>> QcResultsRepo.elasticSearch).mapError(t => TestFailure.Runtime(Cause.die(t)))

    suite("QcResultRoutes") {
      testWithCleanIndexM("POST /qcresults should insert QC results to ElasticSearch") {
        val checkSuiteResultToSave = ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteDescription", Seq.empty, Instant.now, Map.empty)
        for {
          httpStatus <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request[RIO[QcResultsRepo with Logging, *]](Method.POST, uri"/qcresults").withEntity(checkSuiteResultToSave))
              .map(_.status)
          assert200Response = assert(httpStatus)(equalTo(Status.Ok))
          repo <- ZIO.access[QcResultsRepo](_.get)
          dbResults <- repo.getAllCheckSuiteResults.repeatUntil(r => r.nonEmpty)
          isSavedToDb = assert(dbResults)(equalTo(List(checkSuiteResultToSave)))
        } yield assert200Response && isSavedToDb
      } @@ timeout(Duration.ofSeconds(5))
    }.provideCustomLayer(qcResultsRepo ++ Mocks.mockLogger)
  }
}
