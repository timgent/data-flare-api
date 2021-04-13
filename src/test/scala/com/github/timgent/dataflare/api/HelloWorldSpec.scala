package com.github.timgent.dataflare.api

import org.http4s._
import org.http4s.implicits._
import zio.Task
import zio.interop.catz.taskConcurrentInstance
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, _}

object HelloWorldSpec extends DefaultRunnableSpec {

  def spec =
    suite("HelloWorldSpec")(testM("HelloWorld returns status code 200") {
      for {
        res <- retHelloWorld
      } yield assert(res.status)(equalTo(Status.Ok))
    })

//  test("HelloWorld returns hello world message") {
//    assertIO(
//      retHelloWorld.flatMap(x => x.as[String]),
//      "{\"message\":\"Hello, world\"}"
//    )
//  }

//  test("another test") {
//    val getRoot: Request[IO] = Request[Task](Method.GET, uri"/hello/timxx")
//    val helloWorldService: HttpRoutes[Task] =
//      helloWorldRoutes(HelloWorld.impl)
//    val futRes: OptionT[IO, Response[Task]] = helloWorldService.run(getRoot)
////    val x = futRes.value.unsafeRunSync().get.as[String].unsafeRunSync()
//    assertIO(
//      futRes.value.unsafeRunSync().get.as[String],
//      "{\"message\":\"Hello, timxx\"}"
//    )
//  }

  private[this] val retHelloWorld: Task[Response[Task]] = {
    val getHW = Request[Task](Method.GET, uri"/hello/world")
    val helloWorld: HelloWorld = HelloWorld.impl
    DataflareapiRoutes.helloWorldRoutes(helloWorld).orNotFound(getHW)
  }
}
