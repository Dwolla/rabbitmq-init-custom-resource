package com.dwolla
package rabbitmq.users

import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import com.dwolla.aws.SecretsManagerAlg
import com.dwolla.rabbitmq.AbstractCreateOrUpdateHandler
import com.dwolla.rabbitmq.AbstractCreateOrUpdateHandler.logEvent
import com.dwolla.rabbitmq.RabbitMqCommonHandler._
import feral.lambda.INothing
import feral.lambda.cloudformation.CloudFormationRequestType._
import feral.lambda.cloudformation.{CloudFormationCustomResource, HandlerResponse, PhysicalResourceId}
import io.circe.optics.JsonPath
import io.circe.syntax._
import org.http4s.Method.{DELETE, PUT}
import org.http4s.circe.jsonEncoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import org.http4s.{Response, Uri}
import org.typelevel.log4cats.Logger

object RabbitMqUserResource {
  def handleRequest[F[_] : MonadCancelThrow : ByteStreamJsonParser : Logger](client: Client[F],
                                                                             secretsManagerAlg: SecretsManagerAlg[F])
                                                                            (implicit SC: _root_.fs2.Compiler[F, F]): CloudFormationCustomResource[F, RabbitMqUser, INothing] =
    new AbstractCreateOrUpdateHandler[F, RabbitMqUser] with Http4sClientDsl[F] {
      private def putUser(input: RabbitMqUser, userUri: Uri, auth: Authorization): F[PhysicalResourceId] =
        for {
          physicalResourceId <- client.run(PUT(RabbitMqUserDto(input.password, "monitoring").asJson, userUri, auth)).use { res: Response[F] =>
            if (res.status.isSuccess)
              PhysicalResourceId(userUri.renderString)
                .liftTo[F](UserCreationException(input.username, userUri.renderString))
            else
              res
                .body
                .through(ByteStreamJsonParser[F].pipe)
                .compile
                .last
                .map {
                  _
                    .flatMap(JsonPath.root.reason.as[String].getOption)
                    .getOrElse("Unknown reason")
                }
                .flatMap(UserCreationException(input.username, _).raiseError[F, PhysicalResourceId])
          }
        } yield physicalResourceId

      private def putUserPermissions(input: RabbitMqUser, baseUri: Uri, auth: Authorization): F[Unit] =
        client
          .successful(PUT(input.permissions.asJson, baseUri / "api" / "permissions" / rabbitmqVirtualHost / input.username, auth))
          .ifM(().pure[F], Logger[F].warn(s"the user permissions could not be set for user ${input.username}"))

      override def createOrUpdate(input: RabbitMqUser): F[HandlerResponse[INothing]] =
        for {
          auth <- rabbitAuthorizationHeader(secretsManagerAlg)(input.environment)
          id <- putUser(input, input.host.value / "api" / "users" / input.username, auth)
          _ <- putUserPermissions(input, input.host.value, auth)
        } yield HandlerResponse(id, None)

      override def deleteResource(input: RabbitMqUser): F[HandlerResponse[INothing]] =
        (for {
          _ <- logEvent(input, DeleteRequest, None)
          auth <- rabbitAuthorizationHeader(secretsManagerAlg)(input.environment)
          id <- deleteUser(input, input.host.value / "api" / "users" / input.username, auth)
        } yield HandlerResponse(id, None))
          .guaranteeCase { outcome =>
            logEvent(input, DeleteRequest, outcome.some)
          }

      private def deleteUser(input: RabbitMqUser, userUri: Uri, auth: Authorization): F[PhysicalResourceId] =
        client
          .successful(DELETE(input.permissions.asJson, userUri, auth))
          .ifM(().pure[F], UserDeletionException(input.username).raiseError[F, Unit])
          .as(PhysicalResourceId(userUri.renderString).getOrElse(PhysicalResourceId.unsafeApply("unknown")))

    }
}
