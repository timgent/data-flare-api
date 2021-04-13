package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.error.ApiError
import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.checksSuiteResultDecoder
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.{
  circeEntityDecoder,
  circeEntityEncoder
}
import org.http4s.dsl.Http4sDsl
import zio.interop.catz.taskConcurrentInstance
import zio.{RIO, ZIO}

object QcResultsRoutes {
  def getUser(id: Int): ZIO[UserPersistence, ApiError, User] = ???

  val dsl = new Http4sDsl[RIO[QcResultsRepo, *]] {}
  import dsl._

  val getLatestResultsRoute: HttpRoutes[RIO[QcResultsRepo, *]] = HttpRoutes
    .of[RIO[QcResultsRepo, *]] {
      case GET -> Root / "qcresults" =>
        QcResultsRepo.getLatestQcs
          .foldM(
            _ =>
              // TODO: Give a nicer error for an API user, probably depending on the error received
              InternalServerError(
                s"An internal server error occurred".stripMargin
            ),
            Ok(_)
          )
      case req @ POST -> Root / "qcresults" =>
        for {
          checksSuiteResult <- req.as[ChecksSuiteResult]
          res <- QcResultsRepo
            .saveChecksSuiteResult(checksSuiteResult)
            .foldM(
              _ => // TODO: Give a nicer error for an API user, probably depending on the error received
                InternalServerError(
                  s"An internal server error occurred".stripMargin
              ),
              Ok(_)
            )
        } yield res
    }
}
