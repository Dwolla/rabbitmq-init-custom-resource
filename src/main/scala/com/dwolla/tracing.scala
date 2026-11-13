package com.dwolla

import cats._
import cats.effect.{MonadCancel, Resource, Trace => _}
import cats.syntax.all._
import cats.tagless.aop._
import cats.tagless.syntax.all._
import natchez._
import natchez.http4s.implicits._
import org.http4s.Query
import org.http4s.client.Client

object tracing {
  class WithTracingSyntax[Alg[_[_]] : Instrument, F[_] : Trace](f: Alg[F]) {
    private def toTraceFunctionK: Instrumentation[F, *] ~> F = new (Instrumentation[F, *] ~> F) {
      override def apply[A](fa: Instrumentation[F, A]): F[A] = Trace[F].span(s"${fa.algebraName}.${fa.methodName}")(fa.value)
    }

    def withTracing: Alg[F] =
      Instrument[Alg].instrument(f).mapK(toTraceFunctionK)
  }

  implicit def toWithTracingSyntax[Alg[_[_]] : Instrument, F[_] : Trace](f: Alg[F]): WithTracingSyntax[Alg, F] =
    new WithTracingSyntax(f)

  /**
   * Traces requests as natchez-http4s's `NatchezMiddleware.client` does: each request runs in a
   * span named `http4s-client-request`, carries the span's kernel in its headers, and records its
   * method, its URI and the response status. The URI is recorded without its query string.
   */
  def traceRequests[F[_] : Trace](client: Client[F])
                                 (implicit ev: MonadCancel[F, Throwable]): Client[F] =
    Client { req =>
      Resource {
        Trace[F].span("http4s-client-request") {
          for {
            knl <- Trace[F].kernel
            _ <- Trace[F].put(
              "client.http.uri" -> req.uri.copy(query = Query.empty).toString(),
              "client.http.method" -> req.method.toString
            )
            reqWithKernel = req.withHeaders(knl.toHttp4sHeaders ++ req.headers) // prioritize request headers over kernel ones
            rsrc <- client.run(reqWithKernel).allocated
            _ <- Trace[F].put("client.http.status_code" -> rsrc._1.status.code.toString())
          } yield rsrc
        }
      }
    }
}
