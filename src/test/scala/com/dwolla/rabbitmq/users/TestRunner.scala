package com.dwolla
package rabbitmq.users

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import feral.lambda.cloudformation._
import feral.lambda.{INothing, LambdaEnv, TestContext}
import org.http4s.syntax.all._

object TestRunner extends ResourceApp {
  override def run(args: List[String]): Resource[IO, ExitCode] =
    new Handler()
      .handler
      .evalMap { f: (LambdaEnv[IO, CloudFormationCustomResourceRequest[RabbitMqUser]] => IO[Option[INothing]]) =>
        for {
          host <- UriFromHost(host"b-cbbcab50-6f80-47d9-89c5-41612894bb31.mq.us-west-2.amazonaws.com").liftTo[IO]
          env = LambdaEnv.pure(
            CloudFormationCustomResourceRequest(
              CloudFormationRequestType.DeleteRequest,
              uri"https://webhook.site/51e46e78-0661-45ba-8418-b480a6b56bef",
              StackId("foo"),
              RequestId("bar"),
              ResourceType("Custom:RabbitMqInit"),
              LogicalResourceId("AdminUser"),
              PhysicalResourceId = PhysicalResourceId("http://10.200.10.1:15672/api/users/test-user"),
              ResourceProperties = RabbitMqUser("test-user", "Dwolla dev1!", RabbitMqPermissions(configure = "^$", write = ".*", read = ".*"), host, Sandbox),
              OldResourceProperties = None
            ),
            TestContext[IO]
          )
          output <- f(env)
        } yield output
      }
      .as(ExitCode.Success)
}
