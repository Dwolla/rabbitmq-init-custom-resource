package com.dwolla.rabbitmq

import cats._
import cats.data._
import cats.effect._
import cats.effect.std.Random
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.tagless.syntax.all._
import com.comcast.ip4s.Host
import com.dwolla.DwollaEnvironment
import com.dwolla.aws.{SecretId, SecretsManagerAlg}
import com.dwolla.tracing._
import feral.lambda.cloudformation.{CloudFormationCustomResource, CloudFormationCustomResourceRequest}
import feral.lambda.{INothing, KernelSource, LambdaEnv, TracedHandler}
import io.circe._
import monix.newtypes.NewtypeWrapped
import natchez.Span
import natchez.http4s.NatchezMiddleware
import natchez.xray.{XRay, XRayEnvironment}
import org.http4s.circe.decodeUri
import org.http4s.client.Client
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, ParseResult, Uri}
import org.typelevel.log4cats.Logger

object RabbitMqCommonHandler {
  val rabbitmqVirtualHost = "/"

  type UriFromHost = UriFromHost.Type
  object UriFromHost extends NewtypeWrapped[Uri] {
    def apply(host: Host): ParseResult[UriFromHost] =
      Uri
        .fromString(s"https://${host.show}")
        .map(UriFromHost(_))

    implicit val UriFromHostnameDecoder: Decoder[UriFromHost] =
      Decoder[Uri]
        .map(UriFromHost(_))
        .or {
          Decoder[Host]
            .emap(UriFromHost(_).leftMap(_.message))
        }

    implicit val UriFromHostnameEncoder: Encoder[UriFromHost] =
      Encoder[String].contramap(_.value.renderString)
  }

  def rabbitAuthorizationHeader[F[_] : Monad](secretsManagerAlg: SecretsManagerAlg[F])
                                             (env: DwollaEnvironment): F[Authorization] =
    for {
      login_username <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/username"))
      login_password <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/password"))
    } yield Authorization(BasicCredentials(login_username, login_password))
}

class RabbitMqCommonHandler[F[_] : Async : Logger, T](f: (Client[Kleisli[F, Span[F], *]], SecretsManagerAlg[Kleisli[F, Span[F], *]]) => CloudFormationCustomResource[Kleisli[F, Span[F], *], T, INothing],
                                                      logRequestBodies: Boolean) {
  def handler: Resource[F, LambdaEnv[F, CloudFormationCustomResourceRequest[T]] => F[Option[INothing]]] =
    for {
      implicit0(random: Random[F]) <- Random.scalaUtilRandom[F].toResource
      entryPoint <- XRayEnvironment[Resource[F, *]].daemonAddress.flatMap {
        case Some(addr) => XRay.entryPoint(addr)
        case None => XRay.entryPoint[F]()
      }
      client <- httpClient
      secretsManager <- secretsManagerResource
    } yield { implicit env: LambdaEnv[F, CloudFormationCustomResourceRequest[T]] =>
      TracedHandler(entryPoint, Kleisli { (span: Span[F]) =>
        CloudFormationCustomResource[Kleisli[F, Span[F], *], T, INothing](tracedHttpClient(client, span), f(tracedHttpClient(client, span), secretsManager)).run(span)
      })
    }

  protected def httpClient: Resource[F, Client[F]] =
    EmberClientBuilder
      .default[F]
      .build
      .map { client =>
        // only log response bodies from RabbitMQ. the request bodies for user
        // creates/updates are sensitive because they contain the user's password

        ResponseLogger(logHeaders = true, logBody = true)(
          RequestLogger(logHeaders = true, logBody = logRequestBodies)(client)
        )
      }

  private def tracedHttpClient(client: Client[F], span: Span[F]): Client[Kleisli[F, Span[F], *]] =
    NatchezMiddleware.client(client.translate(Kleisli.liftK[F, Span[F]])(Kleisli.applyK(span)))

  protected def secretsManagerResource: Resource[F, SecretsManagerAlg[Kleisli[F, Span[F], *]]] =
    SecretsManagerAlg.resource[F].map(_.mapK(Kleisli.liftK[F, Span[F]]).withTracing)

  /**
   * The XRay kernel comes from environment variables, so we don't need to extract anything from the incoming event
   */
  private implicit def kernelSource[Event]: KernelSource[Event] = KernelSource.emptyKernelSource
}
