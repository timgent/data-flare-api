package com.github.timgent.dataflare.api.json

import com.github.timgent.dataflare.checks.CheckStatus
import io.circe.Decoder

object CustomDecoders {
  implicit val checkStatusDecoder: Decoder[CheckStatus] = {
    Decoder.decodeString.emap(str =>
      CheckStatus.lowerCaseNamesToValuesMap.get(str) match {
        case Some(checkStatus) => Right(checkStatus)
        case None              => Left(s"checkStatus of $str was not a valid check status. Use one of ${CheckStatus.values.toList}")
      }
    )
  }
}
