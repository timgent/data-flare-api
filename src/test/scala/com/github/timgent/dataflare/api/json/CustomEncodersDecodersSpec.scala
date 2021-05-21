package com.github.timgent.dataflare.api.json

import com.github.timgent.dataflare.api.json.CustomEncodersDecoders.{withIdDecoder, withIdEncoder}
import com.github.timgent.dataflare.api.utils.WithId
import com.github.timgent.dataflare.checkssuite.ChecksSuiteResult
import com.github.timgent.dataflare.json.CustomEncodings.checksSuiteResultDecoder
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser._
import io.circe.syntax._
import zio.test.Assertion.equalTo
import zio.test._

case class NumberString(num: Int, str: String)

object CustomEncodersDecodersSpec extends DefaultRunnableSpec {
  implicit val numberStringEncoder = deriveEncoder[NumberString]
  implicit val numberStringDecoder = deriveDecoder[NumberString]
  override def spec = {
    suite("withId encoder and decoder should")(
      test("correctly encode and decode a case class with an ID") {
        val obj = WithId("id", NumberString(1, "a"))
        val objAsJson = obj.asJson
        val expectedJson =
          parse("""{
            | "id": "id",
            | "num": 1,
            | "str": "a"
            |}
            |""".stripMargin).right.get
        assert(objAsJson)(equalTo(expectedJson))
        assert(objAsJson.as[WithId[NumberString]].right.get)(equalTo(obj))
      }
    )
  }
}
