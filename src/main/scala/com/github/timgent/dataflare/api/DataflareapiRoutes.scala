package com.github.timgent.dataflare.api

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.interop.catz._

object DataflareapiRoutes {

  val dsl = new Http4sDsl[Task] {}
  import dsl._

  val helloWorldRoute = HttpRoutes
    .of[Task] {
      case GET -> Root / "hello" => Ok("Hello, Joe")
    }

  def jokeRoutes(J: Jokes): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "joke" =>
        for {
          joke <- J.get
          resp <- Ok(joke)
        } yield resp
    }
  }

  def helloWorldRoutes(H: HelloWorld): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }
}
