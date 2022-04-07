package com.dwolla.rabbitmq.policies

import cats._
import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s.Arbitraries._
import com.dwolla._
import com.dwolla.rabbitmq.Arbitraries._
import com.dwolla.rabbitmq.RabbitMqCommonHandler._
import com.dwolla.rabbitmq.{Arbitraries, FakeSecretsManagerAlg}
import com.eed3si9n.expecty.Expecty.expect
import feral.lambda.cloudformation.PhysicalResourceId
import io.circe.JsonObject
import io.circe.literal.JsonStringContext
import io.circe.testing.instances.arbitraryJsonObject
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.http4s.circe.jsonEncoder
import org.http4s.client._
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpApp, HttpRoutes}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF
import org.scalacheck.{Arbitrary, Gen}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

class HandlerSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite 
    with Arbitraries {

  private implicit def logger[F[_] : Applicative]: Logger[F] = NoOpLogger[F]

  private val genPolicy: Gen[Policy] =
    for {
      pattern <- arbitrary[String]
      definition <- arbitrary[JsonObject]
      priority <- arbitrary[Option[Int]]
      applyTo <- arbitrary[Option[String]]
    } yield Policy(pattern, definition, priority, applyTo)
  private implicit val arbPolicy: Arbitrary[Policy] = Arbitrary(genPolicy)
  
  private val genRabbitMqPolicy: Gen[RabbitMqPolicy] =
    for {
      policyName <- Gen.identifier
      policy <- arbitrary[Policy]
      host <- Gen.oneOf(ipGenerator, hostnameGenerator, idnGenerator).flatMap(UriFromHost(_).fold(_ => Gen.fail, Gen.const))
      env <- arbitrary[DwollaEnvironment]
    } yield RabbitMqPolicy(policyName, policy, host, env)
  private implicit val arbRabbitMqPolicy: Arbitrary[RabbitMqPolicy] = Arbitrary(genRabbitMqPolicy)

  test("creates should return the appropriate physical resource ID") {
    PropF.forAllF { (input: RabbitMqPolicy, adminUsername: RabbitMqAdminUsername, adminPassword: RabbitMqAdminPassword) =>
      val client = Client.fromHttpApp(new RabbitMqApi[IO](input.policyName).rabbitMqApi)

      val secretsManager = new FakeSecretsManagerAlg[IO](adminUsername, adminPassword, input.environment)

      RabbitMqPoliciesResource
        .handleRequest[IO](client, secretsManager)
        .createResource(input)
        .map { output =>
          expect(output.physicalId == PhysicalResourceId.unsafeApply((input.host.value / "api" / "policies" / "/" / input.policyName).renderString))
        }
    }
  }

  test("deletes should return the appropriate physical resource ID") {
    PropF.forAllF { (input: RabbitMqPolicy, adminUsername: RabbitMqAdminUsername, adminPassword: RabbitMqAdminPassword) =>
      val client = Client.fromHttpApp(new RabbitMqApi[IO](input.policyName).rabbitMqApi)
      val secretsManager = new FakeSecretsManagerAlg[IO](adminUsername, adminPassword, input.environment)

      RabbitMqPoliciesResource
        .handleRequest[IO](client, secretsManager)
        .deleteResource(input)
        .map { output =>
          expect(output.physicalId == PhysicalResourceId.unsafeApply((input.host.value / "api" / "policies" / "/" / input.policyName).renderString))
        }
    }
  }

}

class RabbitMqApi[F[_] : Monad](policyName: String) extends Http4sDsl[F] {
  private val putPolicy = HttpRoutes.of[F] {
    case PUT -> Root / "api" / "policies" / "/" / passedPolicyName if passedPolicyName == policyName =>
      Ok()
    case PUT -> Root / "api" / "policies" / "/" / _ =>
      BadRequest(json"""{"reason": "wrong policy name"}""")
  }
   
  private val deletePolicy = HttpRoutes.of[F] {
    case DELETE -> Root / "api" / "policies" / "/" / passedPolicyName if passedPolicyName == policyName =>
      Ok()
    case DELETE -> Root / "api" / "policies" / "/" / _ =>
      BadRequest(json"""{"reason": "wrong policy name"}""")
  }

  val rabbitMqApi: HttpApp[F] = (putPolicy <+> deletePolicy).orNotFound
}
