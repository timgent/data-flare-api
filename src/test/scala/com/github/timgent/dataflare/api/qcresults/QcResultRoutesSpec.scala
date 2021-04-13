package com.github.timgent.dataflare.api.qcresults

import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Method, Request}
import zio.Task
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}

object QcResultRoutesSpec extends DefaultRunnableSpec {
  def spec: ZSpec[TestEnvironment, Any] =
    suite("QcResultRoutes")(
      testM("GET /qcresults/latest should get the latest QC Results") {
        val getHW = Request[Task](Method.GET, uri"/qcresults/latest")
        println(getHW)
//        val helloWorld: HelloWorld = HelloWorld.impl
//        val retHelloWorld: Task[Response[Task]] =
//          DataflareapiRoutes.helloWorldRoutes(helloWorld).orNotFound(getHW)
//        for {
//          res <- retHelloWorld
//        } yield assert(res.status)(equalTo(Status.Ok))
        ???
      }
    )
}
