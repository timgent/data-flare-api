package com.github.timgent.dataflare.api.qcresults

import cats.implicits.toTraverseOps
import com.github.timgent.dataflare.api.json.CustomEncodersDecoders.withIdDecoder
import com.github.timgent.dataflare.api.qcresults.EntityEncoders.checksSuiteResultsEntityEncoder
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.api.utils.{Mocks, WithId}
import com.github.timgent.dataflare.checkssuite.{CheckSuiteStatus, ChecksSuiteResult}
import com.github.timgent.dataflare.json.CustomEncodings.checksSuiteResultDecoder
import com.sksamuel.elastic4s.testkit.DockerTests
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import org.http4s.{Method, Request, Status}
import zio.interop.catz.{monadErrorInstance, taskConcurrentInstance}
import zio.logging.Logging
import zio.random.Random
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Cause, Has, RIO, ZIO, ZLayer}

import java.time.{Duration, Instant, LocalDateTime, ZoneOffset}

object QcResultRoutesSpec extends DefaultRunnableSpec with DockerTests {
  def nextRandomStr() = util.Random.alphanumeric.filter(_.isLetter).sliding(5).map(_.mkString.toLowerCase).next()
  val timeoutSecs = 5
  def testWithCleanIndexM[R, E](testName: String)(
      assertion: => ZIO[R, E, TestResult]
  )(implicit
      loc: SourceLocation,
      ev: TestEnvironment with QcResultsRepo with Logging with Has[ElasticSearchConfig] <:< R with QcResultsRepo with Has[
        ElasticSearchConfig
      ]
  ) = {
    val elasticSearch = ZLayer.succeed(ElasticSearchConfig(List("http://127.0.0.1:9200"), nextRandomStr()))
    val qcResultsRepo: ZLayer[Random with Sized, TestFailure.Runtime[Nothing], QcResultsRepo] =
      (elasticSearch >>> QcResultsRepo.elasticSearch).mapError(t => TestFailure.Runtime(Cause.die(t)))
    testM(testName) {
      for {
        repo <- ZIO.access[QcResultsRepo](_.get)
        _ <- repo.delQcResultsIndex
        _ <- repo.createQcResultsIndex
        a <- assertion
        _ <- repo.delQcResultsIndex
      } yield a
    }.provideCustomLayer(qcResultsRepo ++ Mocks.mockLogger ++ elasticSearch)
  }
  val today = LocalDateTime.now.toInstant(ZoneOffset.UTC)
  val yesterday = LocalDateTime.now.minusDays(1).toInstant(ZoneOffset.UTC)
  val twoDaysAgo = LocalDateTime.now.minusDays(2).toInstant(ZoneOffset.UTC)

  def spec =
    suite("QcResultRoutes")(
      testWithCleanIndexM("POST /qcresults should insert QC results to ElasticSearch") {
        val checkSuiteResultToSave = ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteDescription", Seq.empty, Instant.now, Map.empty)
        for {
          httpStatus <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request[RIO[QcResultsRepo with Logging, *]](Method.POST, uri"/qcresults").withEntity(checkSuiteResultToSave))
              .map(_.status)
          repo <- ZIO.access[QcResultsRepo](_.get)
          dbResults <- repo.getAllCheckSuiteResults.repeatUntil(r => r.nonEmpty)
        } yield assert(httpStatus)(equalTo(Status.Ok)) && assert(dbResults.map(_.value))(equalTo(List(checkSuiteResultToSave)))
      } @@ timeout(Duration.ofSeconds(timeoutSecs)),
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
              .repeatUntilM(_.as[List[QcRun]].either.map(e => e.isRight && e.right.get.size == expectedQcRuns.size))
          body <- res.as[List[WithId[QcRun]]]
        } yield assert(res.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedQcRuns))
      } @@ timeout(Duration.ofSeconds(timeoutSecs)),
      testWithCleanIndexM("GET /qcresults should fetch all QC results") {
        val checkSuiteResults = List(
          WithId("1", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, today, Map.empty)),
          WithId("2", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, yesterday, Map.empty)),
          WithId("3", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("4", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteB", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("5", ChecksSuiteResult(CheckSuiteStatus.Error, "checkSuiteB", Seq.empty, yesterday, Map.empty))
        )
        for {
          repo <- ZIO.access[QcResultsRepo](_.get)
          _ <- checkSuiteResults.traverse(repo.saveCheckSuiteResultWithId)
          res <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request(Method.GET, uri"/qcresults"))
              .repeatUntilM(_.as[List[ChecksSuiteResult]].either.map(e => e.isRight && e.right.get.size == checkSuiteResults.size))
          body <- res.as[List[WithId[ChecksSuiteResult]]]
        } yield assert(res.status)(equalTo(Status.Ok)) && assert(body)(equalTo(checkSuiteResults))
      } @@ timeout(Duration.ofSeconds(timeoutSecs)),
      testWithCleanIndexM(
        "GET /qcresults?checkSuiteDescription=checkSuiteA should fetch only QC results for the given checkSuiteDescription"
      ) {
        val checkSuiteResults = List(
          WithId("1", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, today, Map.empty)),
          WithId("2", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, yesterday, Map.empty)),
          WithId("3", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("4", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteB", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("5", ChecksSuiteResult(CheckSuiteStatus.Error, "checkSuiteB", Seq.empty, yesterday, Map.empty))
        )
        val expectedQcRuns = List(
          WithId("1", QcRun("checkSuiteA", CheckSuiteStatus.Success, today)),
          WithId("2", QcRun("checkSuiteA", CheckSuiteStatus.Success, yesterday)),
          WithId("3", QcRun("checkSuiteA", CheckSuiteStatus.Success, twoDaysAgo))
        )
        for {
          repo <- ZIO.access[QcResultsRepo](_.get)
          _ <- checkSuiteResults.traverse(repo.saveCheckSuiteResultWithId)
          res <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request(Method.GET, uri"/qcresults?checkSuiteDescription=checkSuiteA"))
              .repeatUntilM(
                _.as[List[QcRun]].either.map(maybeQcRun => maybeQcRun.isRight && maybeQcRun.right.get.size == expectedQcRuns.size)
              )
          body <- res.as[List[WithId[QcRun]]]
        } yield assert(res.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedQcRuns))
      } @@ timeout(Duration.ofSeconds(timeoutSecs)),
      testWithCleanIndexM(
        "GET /qcresult/{documentId} should fetch only 1 QC result for the given documentId"
      ) {
        val checkSuiteResults = List(
          WithId("1", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, today, Map.empty)),
          WithId("2", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, yesterday, Map.empty)),
          WithId("3", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("4", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteB", Seq.empty, twoDaysAgo, Map.empty)),
          WithId("5", ChecksSuiteResult(CheckSuiteStatus.Error, "checkSuiteB", Seq.empty, yesterday, Map.empty))
        )
        val expectedChecksSuiteResult =
          WithId("2", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, yesterday, Map.empty))

        for {
          repo <- ZIO.access[QcResultsRepo](_.get)
          _ <- checkSuiteResults.traverse(repo.saveCheckSuiteResultWithId)
          res <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request(Method.GET, uri"/qcresult/2"))
              .repeatUntil(
                _.status == Status.Ok
              )
          body <- res.as[WithId[ChecksSuiteResult]]
        } yield assert(res.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedChecksSuiteResult))
      } @@ timeout(Duration.ofSeconds(timeoutSecs)),
      testWithCleanIndexM(
        "GET /qcresult/{documentId} should return a 404 if the given document id is not present"
      ) {
        val checkSuiteResults = List(
          WithId("1", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, today, Map.empty)),
          WithId("2", ChecksSuiteResult(CheckSuiteStatus.Success, "checkSuiteA", Seq.empty, yesterday, Map.empty))
        )

        for {
          repo <- ZIO.access[QcResultsRepo](_.get)
          _ <- checkSuiteResults.traverse(repo.saveCheckSuiteResultWithId)
          res <-
            QcResultsRoutes.qcResultsRoutes.orNotFound
              .run(Request(Method.GET, uri"/qcresult/notFoundId"))
        } yield assert(res.status)(equalTo(Status.NotFound))
      } @@ timeout(Duration.ofSeconds(timeoutSecs))
    )
}
