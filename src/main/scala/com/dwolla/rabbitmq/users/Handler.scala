package com.dwolla.rabbitmq.users

import cats.effect._
import feral.lambda.cloudformation.CloudFormationCustomResourceRequest
import feral.lambda.{INothing, IOLambda, LambdaEnv}

class Handler extends IOLambda[CloudFormationCustomResourceRequest[RabbitMqUser], INothing] {
  override def handler: Resource[IO, LambdaEnv[IO, CloudFormationCustomResourceRequest[RabbitMqUser]] => IO[Option[INothing]]] =
    new RabbitMqUserResource[IO].handler
}
