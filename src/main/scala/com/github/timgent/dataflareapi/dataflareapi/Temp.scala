package com.github.timgent.dataflareapi.dataflareapi
import cats.FlatMap
import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.github.timgent.dataflareapi.dataflareapi.DataflareapiRoutes.helloWorldRoutes
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{HttpRoutes, Method, Request, Response}

import scala.concurrent.{ExecutionContext, Future}

object Temp {
  implicit val ec = ExecutionContext.global
  final case class Kleisli[F[_], A, B](run: A => F[B]) {
    def compose[Z](k: Kleisli[F, Z, A])(implicit F: FlatMap[F]): Kleisli[F, Z, B] =
      Kleisli[F, Z, B](z => k.run(z).flatMap(run))
  }


  type LeftStringEither[T] = Either[String, T]
  val opKleisli = Kleisli[Option, Int, String](x => Some(x.toString))
  val opRevKleisli = Kleisli[Option, String, Double](x => Some(x.toDouble * 2))
//  val eitherKleisli = Kleisli[LeftStringEither, String, Double](x => Either[String, Double])

  val doubleOpKleisli = opRevKleisli.compose[Int](opKleisli)
  val res: Option[Double] = doubleOpKleisli.run(51)


  val customGreeting: Future[Option[String]] = Future.successful(Some("welcome back, Lola"))
  val defaultGreeting: Future[String] = Future.successful("hello, there")

  val greeting: Future[String] = customGreeting.flatMap(custom =>
    custom.map(Future.successful).getOrElse(defaultGreeting))

  val getRoot: Request[IO] = Request[IO](Method.GET, uri"/hello/timxx")
  val helloWorldService: HttpRoutes[IO] = helloWorldRoutes(HelloWorld.impl[IO])
  val futRes: OptionT[IO, Response[IO]] = helloWorldService.run(getRoot)
  val x: fs2.Stream[IO, Byte] = futRes.value.unsafeRunSync().get.body.head
  val y = x.compile.toList.unsafeRunSync().toString
}
