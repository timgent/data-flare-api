package com.github.timgent.dataflare.api

import cats.effect.Sync
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.circe._
import org.http4s.Method._
import zio.Task
import zio.interop.catz.{monadErrorInstance, taskConcurrentInstance}

trait Jokes {
  def get: Task[Jokes.Joke]
}

object Jokes {
  def apply(implicit ev: Jokes): Jokes = ev

  final case class Joke(joke: String) extends AnyVal
  object Joke {
    implicit val jokeDecoder: Decoder[Joke] = deriveDecoder[Joke]
    implicit def jokeEntityDecoder[F[_]: Sync]: EntityDecoder[F, Joke] =
      jsonOf
    implicit val jokeEncoder: Encoder[Joke] = deriveEncoder[Joke]
    implicit def jokeEntityEncoder: EntityEncoder[Task, Joke] =
      jsonEncoderOf
  }

  final case class JokeError(e: Throwable) extends RuntimeException

  def impl[F[_]: Sync](C: Client[Task]): Jokes = new Jokes {
    val dsl = new Http4sClientDsl[Task] {}
    import dsl._
    def get: Task[Jokes.Joke] = {
      C.expect[Joke](GET(uri"https://icanhazdadjoke.com/"))
        .adaptError { case t => JokeError(t) } // Prevent Client Json Decoding Failure Leaking
    }
  }
}