package com.github.timgent.dataflare.api.qcresults

import cats.Applicative
import com.github.timgent.dataflare.api.utils.WithId
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.checksSuiteResultEncoder
import com.github.timgent.dataflare.api.json.CustomEncodersDecoders.withIdEncoder
import io.circe.Encoder
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf

object Encoders {
  implicit def checksSuiteResultsEntityEncoder[F[_]: Applicative]: EntityEncoder[F, ChecksSuiteResult] = jsonEncoderOf[F, ChecksSuiteResult]

  implicit def withIdEntityEncoder[F[_]: Applicative, T <: Product: Encoder]: EntityEncoder[F, WithId[T]] =
    jsonEncoderOf[F, WithId[T]](withIdEncoder[T])
}
