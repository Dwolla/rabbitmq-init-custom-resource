package com.dwolla.rabbitmq.users

import cats.effect._
import cats.effect.syntax.all._
import com.dwolla.rabbitmq.RabbitMqCommonHandler
import feral.lambda.cloudformation.CloudFormationCustomResourceRequest
import feral.lambda.{INothing, IOLambda, LambdaEnv}
import org.typelevel.log4cats.slf4j.Slf4jLogger

class Handler extends IOLambda[CloudFormationCustomResourceRequest[RabbitMqUser], INothing] {
  override def handler: Resource[IO, LambdaEnv[IO, CloudFormationCustomResourceRequest[RabbitMqUser]] => IO[Option[INothing]]] =
    Slf4jLogger.create[IO].toResource.flatMap { implicit l =>
      new RabbitMqCommonHandler[IO, RabbitMqUser](RabbitMqUserResource.handleRequest, logRequestBodies = false).handler
    }
}
