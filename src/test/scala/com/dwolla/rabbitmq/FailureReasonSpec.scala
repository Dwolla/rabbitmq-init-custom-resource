package com.dwolla.rabbitmq

import cats.effect._
import cats.syntax.all._
import com.dwolla.Sandbox
import com.dwolla.rabbitmq.Arbitraries._
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import com.dwolla.rabbitmq.policies.{Policy, PolicyDefinition, RabbitMqPoliciesResource, RabbitMqPolicy}
import com.dwolla.rabbitmq.users.{RabbitMqPermissions, RabbitMqUser, RabbitMqUserResource}
import feral.lambda.cloudformation.CloudFormationRequestType._
import feral.lambda.cloudformation._
import feral.lambda.{INothing, LambdaEnv, TestContext}
import io.circe.parser
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.syntax.all._
import org.http4s.{HttpApp, Response, Status}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

class FailureReasonSpec extends CatsEffectSuite {
  private implicit val logger: Logger[IO] = NoOpLogger[IO]

  private val passwordMarker = "made-up-password-marker"
  private val brokerHostMarker = "made-up-broker-host.example"
  private val brokerReasonMarker = "made-up-broker-reason-marker"
  private val brokerUri = UriFromHost(uri"https://made-up-broker-host.example")

  private val secretsManager =
    new FakeSecretsManagerAlg[IO](RabbitMqAdminUsername("madeupadmin"), RabbitMqAdminPassword("made-up-admin-password"), Sandbox)

  /**
   * A broker that refuses every request with a 400 whose reason repeats the request body.
   */
  private val refusingBroker: Client[IO] =
    Client.fromHttpApp(HttpApp[IO] { req =>
      req.as[String].map { body =>
        Response[IO](Status.BadRequest).withEntity(Map("error" -> "bad_request", "reason" -> s"$brokerReasonMarker $body").asJson)
      }
    })

  private val markers = List(passwordMarker, brokerHostMarker, brokerReasonMarker)

  test("a user the broker refuses is reported by user name and response status") {
    val user = RabbitMqUser("madeupuser", passwordMarker, RabbitMqPermissions("^$", ".*", ".*"), brokerUri, Sandbox)

    failureResponse(CreateRequest, user, RabbitMqUserResource.handleRequest[IO](refusingBroker, secretsManager)).map { case (reason, body) =>
      assertEquals(reason, "Failed to create user madeupuser: RabbitMQ responded 400 bad_request")
      assertEquals(markers.filter(body.contains), List.empty[String])
    }
  }

  test("a user the broker refuses without a JSON body is reported by user name and response status") {
    val user = RabbitMqUser("madeupuser", passwordMarker, RabbitMqPermissions("^$", ".*", ".*"), brokerUri, Sandbox)
    val plainTextBroker: Client[IO] =
      Client.fromHttpApp(HttpApp[IO](_ => Response[IO](Status.BadGateway).withEntity(s"$brokerReasonMarker not JSON").pure[IO]))

    failureResponse(CreateRequest, user, RabbitMqUserResource.handleRequest[IO](plainTextBroker, secretsManager)).map { case (reason, body) =>
      assertEquals(reason, "Failed to create user madeupuser: RabbitMQ responded 502")
      assertEquals(markers.filter(body.contains), List.empty[String])
    }
  }

  test("a policy the broker refuses is reported by policy name") {
    failureResponse(CreateRequest, policy, RabbitMqPoliciesResource.handleRequest[IO](refusingBroker, secretsManager)).map { case (reason, body) =>
      assertEquals(reason, "Could not create or update policy madeuppolicy")
      assertEquals(markers.filter(body.contains), List.empty[String])
    }
  }

  test("a policy the broker will not remove is reported by policy name") {
    failureResponse(DeleteRequest, policy, RabbitMqPoliciesResource.handleRequest[IO](refusingBroker, secretsManager)).map { case (reason, body) =>
      assertEquals(reason, "Could not remove policy madeuppolicy")
      assertEquals(markers.filter(body.contains), List.empty[String])
    }
  }

  private lazy val policy =
    RabbitMqPolicy("madeuppolicy", Policy(".*", PolicyDefinition("all", None, "automatic", None), None, None), brokerUri, Sandbox)

  /**
   * Runs one request through the custom resource and returns the failure reason and the
   * whole response body sent to CloudFormation.
   */
  private def failureResponse[A](requestType: CloudFormationRequestType,
                                 properties: A,
                                 resource: CloudFormationCustomResource[IO, A, INothing]): IO[(String, String)] =
    Ref[IO].of(List.empty[String]).flatMap { responses =>
      implicit val env: LambdaEnv[IO, CloudFormationCustomResourceRequest[A]] =
        LambdaEnv.pure(request(requestType, properties), TestContext[IO])

      CloudFormationCustomResource(capturingClient(responses), resource) >>
        responses.get.flatMap {
          case List(body) =>
            parser.decode[CloudFormationCustomResourceResponse](body).liftTo[IO].map { response =>
              assertEquals(response.Status, RequestResponseStatus.Failed)
              (response.Reason.getOrElse(""), body)
            }
          case other => IO.raiseError(new AssertionError(s"expected one response, got ${other.length}"))
        }
    }

  private def request[A](requestType: CloudFormationRequestType, properties: A): CloudFormationCustomResourceRequest[A] =
    CloudFormationCustomResourceRequest(
      requestType,
      uri"https://made-up-response.example/response",
      StackId("made-up-stack"),
      RequestId("made-up-request"),
      ResourceType("Custom::RabbitMq"),
      LogicalResourceId("MadeUp"),
      None,
      properties,
      None,
    )

  private def capturingClient(responses: Ref[IO, List[String]]): Client[IO] =
    Client.fromHttpApp(HttpApp[IO] { req =>
      req.as[String].flatMap(body => responses.update(_ :+ body)).as(Response[IO](Status.Ok))
    })
}
