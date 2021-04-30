package com.github.timgent.dataflare.api.json

import com.github.timgent.dataflare.api.utils.WithId
import com.github.timgent.dataflare.checks.CheckStatus
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}

object CustomEncodersDecoders {
  implicit val checkStatusDecoder: Decoder[CheckStatus] = {
    Decoder.decodeString.emap(str =>
      CheckStatus.lowerCaseNamesToValuesMap.get(str) match {
        case Some(checkStatus) => Right(checkStatus)
        case None              => Left(s"checkStatus of $str was not a valid check status. Use one of ${CheckStatus.values.toList}")
      }
    )
  }

  implicit def withIdEncoder[T <: Product: Encoder]: Encoder[WithId[T]] =
    new Encoder[WithId[T]] {
      override def apply(a: WithId[T]): Json = {
        val tJson = implicitly[Encoder[T]].apply(a.value)
        tJson.asObject match {
          case Some(jsonObject) => Json.fromJsonObject(jsonObject.add("id", Json.fromString(a.id)))
          case None             => tJson
        }
      }
    }

  implicit def withIdDecoder[T <: Product: Decoder]: Decoder[WithId[T]] =
    new Decoder[WithId[T]] {
      override def apply(c: HCursor): Result[WithId[T]] = {
        for {
          id <- c.downField("id").as[String]
          value <- implicitly[Decoder[T]].apply(c)
        } yield WithId(id, value)
      }
    }
}
