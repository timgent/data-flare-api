package com.github.timgent.dataflare.api.qcresults

import cats.implicits.toTraverseOps
import com.github.timgent.dataflare.api.qcresults.Encoders.checksSuiteResultsEntityEncoder
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.api.utils.{Mocks, WithId}
import com.github.timgent.dataflare.checkssuite.{CheckSuiteStatus, ChecksSuiteResult}
import com.sksamuel.elastic4s.testkit.DockerTests
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import org.http4s.{Method, Request, Status}
import zio.interop.catz.{monadErrorInstance, taskConcurrentInstance}
import zio.logging.Logging
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Cause, RIO, ZIO, ZLayer}
import com.github.timgent.dataflare.api.json.CustomEncodersDecoders.withIdDecoder
import java.time.{Duration, Instant, LocalDateTime, ZoneOffset}

object QcResultRoutesSpec extends DefaultRunnableSpec with DockerTests {

  val testIndex = "test-index"
  def testWithCleanIndexM[R, E](testName: String)(assertion: => ZIO[R, E, TestResult])(implicit loc: SourceLocation) = {
    testM(testName) {
      for {
        repo <- ZIO.access[QcResultsRepo](_.get)
        _ <- repo.delQcResultsIndex
        _ <- repo.createQcResultsIndex
        a <- assertion
      } yield a
    }
  }
  val today = LocalDateTime.now.toInstant(ZoneOffset.UTC)
  val yesterday = LocalDateTime.now.minusDays(1).toInstant(ZoneOffset.UTC)
  val twoDaysAgo = LocalDateTime.now.minusDays(2).toInstant(ZoneOffset.UTC)

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
          repo <- ZIO.access[QcResultsRepo](_.get)
          dbResults <- repo.getAllCheckSuiteResults.repeatUntil(r => r.nonEmpty)
        } yield assert(httpStatus)(equalTo(Status.Ok)) && assert(dbResults)(equalTo(List(checkSuiteResultToSave)))
      } @@ timeout(Duration.ofSeconds(5))

      testWithCleanIndexM("GET /qcresults/latest should fetch the latest QC result for each distinct checkSuiteDescription") {
        val checkSuiteResults = List(
          WithId("1", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, today, Map.empty)),
          WithId("2", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, yesterday, Map.empty)),
          WithId("3", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("4", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteB", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("5", ChecksSuiteResult(CheckSuiteStatus.Error, "checkSuiteB", Seq.empty, yesterday, Map.empty))
        )
        val expectedQcRuns = List(
          WithId("1", QcRun("checkSuiteA", CheckSuiteStatus.Success, today)),
          WithId("5", QcRun("checkSuiteB", CheckSuiteStatus.Error, yesterday))
        )
        for {
          repo <- ZIO.access[QcResultsRepo](_.get)
          _ <- checkSuiteResults.traverse(repo.saveCheckSuiteResultWithId)
          res <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request(Method.GET, uri"/qcresults/latest"))
              .repeatUntilM(_.as[List[QcRun]].either.map(e => e.isRight && e.right.get.nonEmpty))
          body <- res.as[List[WithId[QcRun]]]
        } yield assert(res.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedQcRuns))
      } @@ timeout(Duration.ofSeconds(5))
    }.provideCustomLayer(qcResultsRepo ++ Mocks.mockLogger)
  }
}
