package com.dwolla.rabbitmq.policies

import cats.MonadThrow
import cats.syntax.all._
import com.dwolla.aws.SecretsManagerAlg
import com.dwolla.rabbitmq.RabbitMqCommonHandler._
import feral.lambda.INothing
import feral.lambda.cloudformation.{CloudFormationCustomResource, HandlerResponse, PhysicalResourceId}
import io.circe.syntax._
import org.http4s.Method.{DELETE, PUT}
import org.http4s.Uri
import org.http4s.circe.jsonEncoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization

case class PolicyCreationException(policyName: String, 
                                   policyUri: Uri) extends RuntimeException(s"Could not create or update $policyName at ${policyUri.renderString}")

case class PolicyRemovalException(policyUri: Uri) extends RuntimeException(s"Could not remove policy at ${policyUri.renderString}")

object RabbitMqPoliciesResource {
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
          auth <- rabbitAuthorizationHeader(secretsManagerAlg)(input.environment)
          id <- putPolicy(input.policyName, input.policy, policyUri(input.host.value, input.policyName), auth)
        } yield HandlerResponse(id, None)

      override def createResource(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        createOrUpdate(input)

      override def updateResource(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        createOrUpdate(input)

      override def deleteResource(input: RabbitMqPolicy): F[HandlerResponse[INothing]] =
        for {
          auth <- rabbitAuthorizationHeader(secretsManagerAlg)(input.environment)
          uri = policyUri(input.host.value, input.policyName)
          physicalId <- PhysicalResourceId(uri.renderString).liftTo[F](PolicyRemovalException(uri))
          _ <- client.successful(DELETE(uri, auth)).ifM(().pure[F], PolicyRemovalException(uri).raiseError)
        } yield HandlerResponse(physicalId, None)
        
    }
}
