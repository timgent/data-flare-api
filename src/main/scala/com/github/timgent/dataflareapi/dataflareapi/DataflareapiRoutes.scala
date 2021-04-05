package com.github.timgent.dataflareapi.dataflareapi

import cats.effect.{IO, Sync}
import cats.implicits._
import org.http4s.{HttpRoutes, Response, Status}
import org.http4s.dsl.Http4sDsl

object DataflareapiRoutes {

  def jokeRoutes[F[_]: Sync](J: Jokes[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "joke" =>
        for {
          joke <- J.get
          resp <- Ok(joke)
        } yield resp
    }
  }

  def helloWorldRoutes[F[_]: Sync](H: HelloWorld[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          greeting <- H.hello(HelloWorld.Name(name))
          resp <- Ok(greeting)
        } yield resp
    }
  }

  def moo = {
    val service = HttpRoutes.of[IO] {
      case _ => {
        val x: IO[Response[IO]] = IO(Response(Status.Ok))
        x
      }
    }
    service
  }
}