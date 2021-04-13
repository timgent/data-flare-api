package com.github.timgent.dataflare.api.qcresults

import com.github.timgent.dataflare.api.json.CustomDecoders.checkStatusDecoder
import com.github.timgent.dataflare.checks.CheckStatus
import com.github.timgent.dataflare.json.CustomEncodings.checkStatusEncoder
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant

case class QcRun(checkSuiteDescription: String,
                 overallStatus: CheckStatus,
                 timestamp: Instant,
                 isSelected: Boolean,
                 id: Int)

object QcRun {
  implicit val qcRunDecoder = deriveDecoder[QcRun]
  implicit val qcRunEncoder = deriveEncoder[QcRun]
}
