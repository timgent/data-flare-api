package com.github.timgent.dataflareapi.dataflareapi

import cats.data.OptionT
import cats.effect.IO
import com.github.timgent.dataflareapi.dataflareapi.DataflareapiRoutes.helloWorldRoutes
import org.http4s._
import org.http4s.implicits._
import munit.CatsEffectSuite

class HelloWorldSpec extends CatsEffectSuite {

  test("HelloWorld returns status code 200") {
    assertIO(retHelloWorld.map(_.status) ,Status.Ok)
  }

  test("HelloWorld returns hello world message") {
    assertIO(retHelloWorld.flatMap(x => x.as[String]), "{\"message\":\"Hello, world\"}")
  }

  test("another test") {
    val getRoot: Request[IO] = Request[IO](Method.GET, uri"/hello/timxx")
    val helloWorldService: HttpRoutes[IO] = helloWorldRoutes(HelloWorld.impl[IO])
    val futRes: OptionT[IO, Response[IO]] = helloWorldService.run(getRoot)
//    val x = futRes.value.unsafeRunSync().get.as[String].unsafeRunSync()
    assertIO (futRes.value.unsafeRunSync().get.as[String], "{\"message\":\"Hello, timxx\"}")
  }

  private[this] val retHelloWorld: IO[Response[IO]] = {
    val getHW = Request[IO](Method.GET, uri"/hello/world")
    val helloWorld: HelloWorld[IO] = HelloWorld.impl[IO]
    DataflareapiRoutes.helloWorldRoutes(helloWorld).orNotFound(getHW)
  }
}