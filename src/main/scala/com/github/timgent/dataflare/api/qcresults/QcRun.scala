package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.json.CustomDecoders.checkStatusDecoder
import com.github.timgent.dataflare.checkssuite.CheckSuiteStatus
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

case class QcRun(checkSuiteDescription: String, overallStatus: CheckSuiteStatus, timestamp: Instant)

object QcRun {
  val checkSuiteDescriptionField = "checkSuiteDescription"
  implicit val qcRunDecoder: Decoder[QcRun] = deriveDecoder[QcRun]
  implicit val qcRunEncoder: Encoder[QcRun] = deriveEncoder[QcRun]
}
