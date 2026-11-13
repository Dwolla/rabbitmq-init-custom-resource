package com.dwolla.rabbitmq

import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import munit.CatsEffectSuite
import natchez.{Span, TraceValue}
import org.http4s._
import org.http4s.client.Client
import org.http4s.syntax.all._
import org.typelevel.ci.CIString

class TraceRequestsSpec extends CatsEffectSuite {
  private val queryMarker = "made-up-query-marker"

  /**
   * Answers 200 only when the request carries the span's kernel, and 400 otherwise.
   */
  private val inMemoryClient: Client[IO] =
    Client.fromHttpApp(HttpApp[IO] { req =>
      val carriesKernel = req.headers.get(CIString(RecordingSpan.kernelHeader)).exists(_.head.value == RecordingSpan.kernelValue)
      Response[IO](if (carriesKernel) Status.Ok else Status.BadRequest).pure[IO]
    })

  test("each request carries the span's kernel and is traced with its method, its URI without the query string, and the response status") {
    val request = Request[Kleisli[IO, Span[IO], *]](Method.PUT, uri"https://made-up-bucket.example/made-up/path".withQueryParam("X-Made-Up-Signature", queryMarker))

    RecordingSpan.root.flatMap { case (root, recorded) =>
      RabbitMqCommonHandler.decorate(inMemoryClient, root).status(request).run(root) >> recorded
    }.map { spans =>
      assertEquals(spans.map(_.name), List("http4s-client-request"))
      assertEquals(spans.map(_.fields), List(Map[String, TraceValue](
        "client.http.uri" -> "https://made-up-bucket.example/made-up/path",
        "client.http.method" -> "PUT",
        "client.http.status_code" -> "200",
      )))
    }
  }
}
