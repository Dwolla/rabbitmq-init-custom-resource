package com.dwolla.rabbitmq.policies

import cats.effect._
import cats.effect.syntax.all._
import com.dwolla.rabbitmq.RabbitMqCommonHandler
import feral.lambda.cloudformation.CloudFormationCustomResourceRequest
import feral.lambda.{INothing, IOLambda, LambdaEnv}
import org.typelevel.log4cats.slf4j.Slf4jLogger

class Handler extends IOLambda[CloudFormationCustomResourceRequest[RabbitMqPolicy], INothing] {
  override def handler: Resource[IO, LambdaEnv[IO, CloudFormationCustomResourceRequest[RabbitMqPolicy]] => IO[Option[INothing]]] =
    Slf4jLogger.create[IO].toResource.flatMap { implicit l =>
      new RabbitMqCommonHandler[IO, RabbitMqPolicy](RabbitMqPoliciesResource.handleRequest, logRequestBodies = true).handler
    }
}
