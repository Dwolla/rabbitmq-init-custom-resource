package com.dwolla
package rabbitmq.users

import cats.data._
import cats.effect._
import cats.effect.std.Random
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.tagless.syntax.all._
import com.dwolla.aws.{SecretId, SecretsManagerAlg}
import com.dwolla.rabbitmq.users.RabbitMqUserResource.handleRequest
import com.dwolla.tracing._
import feral.lambda.cloudformation.{CloudFormationCustomResource, CloudFormationCustomResourceRequest, HandlerResponse, PhysicalResourceId}
import feral.lambda.{INothing, KernelSource, LambdaEnv, TracedHandler}
import io.circe.syntax._
import natchez.Span
import natchez.http4s.NatchezMiddleware
import natchez.xray.{XRay, XRayEnvironment}
import org.http4s.Method.{DELETE, PUT}
import org.http4s.circe.jsonEncoder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.{Client, middleware}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Response, Uri}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object RabbitMqUserResource {
  val rabbitmqVirtualHost = "/"

  def handleRequest[F[_] : MonadCancelThrow : ByteStreamJsonParser : Logger](client: Client[F],
                                                                             secretsManagerAlg: SecretsManagerAlg[F])
                                                                            (implicit SC: _root_.fs2.Compiler[F, F]): CloudFormationCustomResource[F, RabbitMqUser, INothing] =
    new CloudFormationCustomResource[F, RabbitMqUser, INothing] with Http4sClientDsl[F] {
      private def rabbitAuthorizationHeader(env: DwollaEnvironment): F[Authorization] =
        for {
          login_username <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/username"))
          login_password <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/password"))
        } yield Authorization(BasicCredentials(login_username, login_password))

      private def putUser(input: RabbitMqUser, userUri: Uri, auth: Authorization): F[PhysicalResourceId] =
        for {
          physicalResourceId <- client.run(PUT(RabbitMqUserDto(input.password, "monitoring").asJson, userUri, auth)).use { res: Response[F] =>
            if (res.status.isSuccess)
              PhysicalResourceId(userUri.renderString)
                .liftTo[F](UserCreationException(input.username, userUri.renderString))
            else res.body.through(ByteStreamJsonParser[F].pipe).compile.last.flatMap { maybeJson =>
              // TODO use optics
              val reason =
                maybeJson
                  .flatMap(_.asObject)
                  .flatMap(_("reason").flatMap(_.as[String].toOption))
                  .getOrElse("Unknown reason")

              UserCreationException(input.username, reason).raiseError[F, PhysicalResourceId]
            }
          }
        } yield physicalResourceId

      private def putUserPermissions(input: RabbitMqUser, baseUri: Uri, auth: Authorization): F[Unit] =
        client
          .successful(PUT(input.permissions.asJson, baseUri / "api" / "permissions" / rabbitmqVirtualHost / input.username, auth))
          .ifM(().pure[F], Logger[F].warn(s"the user permissions could not be set for user ${input.username}"))

      // TODO move this to JSON decoder
      private def baseUri(input: RabbitMqUser): F[Uri] =
        Uri.fromString(s"https://${input.host}").liftTo[F]

      private def createOrUpdate(input: RabbitMqUser): F[HandlerResponse[INothing]] =
        for {
          baseUri <- baseUri(input)
          auth <- rabbitAuthorizationHeader(input.environment)
          id <- putUser(input, baseUri / "api" / "users" / input.username, auth)
          _ <- putUserPermissions(input, baseUri, auth)
        } yield HandlerResponse(id, None)

      override def createResource(input: RabbitMqUser): F[HandlerResponse[INothing]] =
        createOrUpdate(input)

      override def updateResource(input: RabbitMqUser): F[HandlerResponse[INothing]] =
        createOrUpdate(input)

      override def deleteResource(input: RabbitMqUser): F[HandlerResponse[INothing]] =
        for {
          baseUri <- baseUri(input)
          auth <- rabbitAuthorizationHeader(input.environment)
          id <- deleteUser(input, baseUri / "api" / "users" / input.username, auth)
        } yield HandlerResponse(id, None)

      private def deleteUser(input: RabbitMqUser, userUri: Uri, auth: Authorization): F[PhysicalResourceId] =
        client
          .successful(DELETE(input.permissions.asJson, userUri, auth))
          .ifM(().pure[F], UserDeletionException(input.username).raiseError[F, Unit])
          .as(PhysicalResourceId(userUri.renderString).getOrElse(PhysicalResourceId.unsafeApply("unknown")))

    }
}

class RabbitMqUserResource[F[_] : Async] {
  def handler: Resource[F, LambdaEnv[F, CloudFormationCustomResourceRequest[RabbitMqUser]] => F[Option[INothing]]] =
    for {
      implicit0(logger: Logger[F]) <- Slf4jLogger.create[F].toResource
      implicit0(random: Random[F]) <- Random.scalaUtilRandom[F].toResource
      entryPoint <- XRayEnvironment[Resource[F, *]].daemonAddress.flatMap {
        case Some(addr) => XRay.entryPoint(addr)
        case None => XRay.entryPoint[F]()
      }
      client <- httpClient
      secretsManager <- secretsManagerResource
    } yield { implicit env: LambdaEnv[F, CloudFormationCustomResourceRequest[RabbitMqUser]] =>
      TracedHandler(entryPoint, Kleisli { (span: Span[F]) =>
        CloudFormationCustomResource[Kleisli[F, Span[F], *], RabbitMqUser, INothing](tracedHttpClient(client, span), handleRequest(tracedHttpClient(client, span), secretsManager)).run(span)
      })
    }

  protected def httpClient: Resource[F, Client[F]] =
    EmberClientBuilder
      .default[F]
      .build
      .map(middleware.Logger[F](logHeaders = true, logBody = true))  // TODO don't log rabbitmq passwords!

  private def tracedHttpClient(client: Client[F], span: Span[F]): Client[Kleisli[F, Span[F], *]] =
    NatchezMiddleware.client(client.translate(Kleisli.liftK[F, Span[F]])(Kleisli.applyK(span)))

  protected def secretsManagerResource(implicit L: Logger[F]): Resource[F, SecretsManagerAlg[Kleisli[F, Span[F], *]]] =
    SecretsManagerAlg.resource[F].map(_.mapK(Kleisli.liftK[F, Span[F]]).withTracing)

  /**
   * The XRay kernel comes from environment variables, so we don't need to extract anything from the incoming event
   */
  private implicit def kernelSource[Event]: KernelSource[Event] = KernelSource.emptyKernelSource
}
