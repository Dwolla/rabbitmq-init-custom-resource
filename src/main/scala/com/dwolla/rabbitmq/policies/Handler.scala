package com.dwolla.rabbitmq.policies

import cats.effect._
import feral.lambda.cloudformation.CloudFormationCustomResourceRequest
import feral.lambda.{INothing, IOLambda, LambdaEnv}

class Handler extends IOLambda[CloudFormationCustomResourceRequest[RabbitMqPolicy], INothing] {
  override def handler: Resource[IO, LambdaEnv[IO, CloudFormationCustomResourceRequest[RabbitMqPolicy]] => IO[Option[INothing]]] =
    new RabbitMqPoliciesResource[IO].handler
}
