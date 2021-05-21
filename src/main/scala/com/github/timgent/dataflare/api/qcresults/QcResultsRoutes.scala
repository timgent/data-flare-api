package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.error.QcResultsRepoErr
import com.github.timgent.dataflare.api.json.CustomEncodersDecoders.withIdEncoder
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.{checksSuiteResultDecoder, checksSuiteResultEncoder}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.dsl.Http4sDsl
import zio.RIO
import zio.interop.catz.taskConcurrentInstance
import zio.logging._

object QcResultsRoutes {
  val dsl = new Http4sDsl[RIO[QcResultsRepo with Logging, *]] {}
  import dsl._

  private def logErrAndReturn500(e: QcResultsRepoErr) =
    for {
      _ <- e.logErr
      res <- InternalServerError(s"An internal server error occurred, ${e.message}".stripMargin)
    } yield res

  val qcResultsRoutes = HttpRoutes
    .of[RIO[QcResultsRepo with Logging, *]] {
      case GET -> Root / "qcresults" =>
        QcResultsRepo.getAllCheckSuiteResults.foldM(logErrAndReturn500, Ok(_))
      case GET -> Root / "qcresults" / "latest" =>
        QcResultsRepo.getLatestQcs.foldM(logErrAndReturn500, Ok(_))
      case GET -> Root / "qcresults" :? CheckSuiteDescriptionParamMatcher(checkSuiteDescription) =>
        QcResultsRepo.getQcsByDescription(checkSuiteDescription).foldM(logErrAndReturn500, Ok(_))
      case DELETE -> Root / "qcresults" / id =>
        QcResultsRepo.deleteQcResult(id).foldM(logErrAndReturn500, Ok(_))
      case GET -> Root / "qcresult" / id =>
        QcResultsRepo
          .getChecksSuiteResult(id)
          .foldM(
            logErrAndReturn500,
            {
              case Some(checkSuiteResults) => Ok(checkSuiteResults)
              case None                    => NotFound()
            }
          )
      case req @ POST -> Root / "qcresults" =>
        for {
          checksSuiteResult <- req.as[ChecksSuiteResult]
          res <- QcResultsRepo.saveChecksSuiteResult(checksSuiteResult).foldM(logErrAndReturn500, Ok(_))
        } yield res
    }

  private object CheckSuiteDescriptionParamMatcher extends QueryParamDecoderMatcher[String]("checkSuiteDescription")
}
