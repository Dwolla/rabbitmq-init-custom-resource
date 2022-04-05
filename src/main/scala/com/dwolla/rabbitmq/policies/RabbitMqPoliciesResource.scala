package com.dwolla.rabbitmq.policies

import cats.MonadThrow
import cats.data._
import cats.effect._
import cats.effect.std.Random
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.tagless.syntax.all._
import com.dwolla.RabbitMqCommonHandler._
import com.dwolla.aws.SecretsManagerAlg
import com.dwolla.rabbitmq.policies.RabbitMqPoliciesResource.handleRequest
import com.dwolla.tracing._
import feral.lambda.cloudformation.{CloudFormationCustomResource, CloudFormationCustomResourceRequest, HandlerResponse, PhysicalResourceId}
import feral.lambda.{INothing, KernelSource, LambdaEnv, TracedHandler}
import io.circe.syntax._
import natchez.Span
import natchez.http4s.NatchezMiddleware
import natchez.xray.{XRay, XRayEnvironment}
import org.http4s.Method.{DELETE, PUT}
import org.http4s.Uri
import org.http4s.circe.jsonEncoder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.{Client, middleware}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

case class PolicyCreationException(policyName: String, 
                                   policyUri: Uri) extends RuntimeException(s"Could not create or update $policyName at ${policyUri.renderString}")

case class PolicyRemovalException(policyUri: Uri) extends RuntimeException(s"Could not remove policy at ${policyUri.renderString}")

object RabbitMqPoliciesResource {
  val rabbitmqVirtualHost = "/"

  def handleRequest[F[_] : MonadThrow](client: Client[F],
                                       secretsManagerAlg: SecretsManagerAlg[F]): CloudFormationCustomResource[F, RabbitMqPolicy, INothing] =
    new CloudFormationCustomResource[F, RabbitMqPolicy, INothing] with Http4sClientDsl[F] {
      private def putPolicy(policyName: String,
                            policy: Policy,
                            policyUri: Uri,
                            auth: Authorization,
                           ): F[PhysicalResourceId] =
        for {
          physicalId <- PhysicalResourceId(policyUri.renderString).liftTo[F](PolicyCreationException(policyName, policyUri))
          out <- client.successful(PUT(policy.asJson, policyUri, auth))
            .ifM(physicalId.pure[F], PolicyCreationException(policyName, policyUri).raiseError)
        } yield out

      private def policyUri(baseUri: Uri, policyName: String): Uri =
        baseUri / "api" / "policies" / rabbitmqVirtualHost / policyName
      
      private def createOrUpdate(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        for {
          baseUri <- baseUri[F](input.host)
          auth <- rabbitAuthorizationHeader(secretsManagerAlg)(input.environment)
          id <- putPolicy(input.policyName, input.policy, policyUri(baseUri, input.policyName), auth)
        } yield HandlerResponse(id, None)

      override def createResource(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        createOrUpdate(input)

      override def updateResource(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        createOrUpdate(input)

      override def deleteResource(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        for {
          baseUri <- baseUri[F](input.host)
          auth <- rabbitAuthorizationHeader(secretsManagerAlg)(input.environment)
          uri = policyUri(baseUri, input.policyName)
          physicalId <- PhysicalResourceId(uri.renderString).liftTo[F](PolicyRemovalException(uri))
          _ <- client.successful(DELETE(uri, auth)).ifM(().pure[F], PolicyRemovalException(uri).raiseError)
        } yield HandlerResponse(physicalId, None)
        
    }
}

class RabbitMqPoliciesResource[F[_] : Async] {
  def handler: Resource[F, LambdaEnv[F, CloudFormationCustomResourceRequest[RabbitMqPolicy]] => F[Option[INothing]]] =
    for {
      implicit0(logger: Logger[F]) <- Slf4jLogger.create[F].toResource
      implicit0(random: Random[F]) <- Random.scalaUtilRandom[F].toResource
      entryPoint <- XRayEnvironment[Resource[F, *]].daemonAddress.flatMap {
        case Some(addr) => XRay.entryPoint(addr)
        case None => XRay.entryPoint[F]()
      }
      client <- httpClient
      secretsManager <- secretsManagerResource
    } yield { implicit env: LambdaEnv[F, CloudFormationCustomResourceRequest[RabbitMqPolicy]] =>
      TracedHandler(entryPoint, Kleisli { (span: Span[F]) =>
        CloudFormationCustomResource[Kleisli[F, Span[F], *], RabbitMqPolicy, INothing](tracedHttpClient(client, span), handleRequest(tracedHttpClient(client, span), secretsManager)).run(span)
      })
    }

  protected def httpClient: Resource[F, Client[F]] =
    EmberClientBuilder
      .default[F]
      .build
      .map(middleware.Logger[F](logHeaders = true, logBody = true))

  private def tracedHttpClient(client: Client[F], span: Span[F]): Client[Kleisli[F, Span[F], *]] =
    NatchezMiddleware.client(client.translate(Kleisli.liftK[F, Span[F]])(Kleisli.applyK(span)))

  protected def secretsManagerResource(implicit L: Logger[F]): Resource[F, SecretsManagerAlg[Kleisli[F, Span[F], *]]] =
    SecretsManagerAlg.resource[F].map(_.mapK(Kleisli.liftK[F, Span[F]]).withTracing)

  /**
   * The XRay kernel comes from environment variables, so we don't need to extract anything from the incoming event
   */
  private implicit def kernelSource[Event]: KernelSource[Event] = KernelSource.emptyKernelSource
}
