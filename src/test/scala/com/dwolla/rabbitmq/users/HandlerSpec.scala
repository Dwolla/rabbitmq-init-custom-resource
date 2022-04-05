package com.dwolla.rabbitmq.users

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla._
import com.dwolla.rabbitmq.Arbitraries._
import com.dwolla.rabbitmq.{Arbitraries, FakeSecretsManagerAlg}
import com.eed3si9n.expecty.Expecty.expect
import feral.lambda.cloudformation.PhysicalResourceId
import io.circe.literal.JsonStringContext
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

  private implicit val logger: Logger[IO] = NoOpLogger[IO]

  private val genRabbitMqPermissions: Gen[RabbitMqPermissions] = {
    val genPermission = Gen.oneOf("^$", ".*")

    for {
      configure <- genPermission
      write <- genPermission
      read <- genPermission
    } yield RabbitMqPermissions(configure, write, read)
  }
  private implicit val arbRabbitMqPermissions: Arbitrary[RabbitMqPermissions] = Arbitrary(genRabbitMqPermissions)

  private val genRabbitMqUser: Gen[RabbitMqUser] =
    for {
      username <- Gen.identifier
      password <- arbitrary[String]
      host <- Gen.identifier // TODO use generator from http4s?
      permissions <- arbitrary[RabbitMqPermissions]
      env <- arbitrary[DwollaEnvironment]
    } yield RabbitMqUser(username, password, permissions, host, env)
  private implicit val arbRabbitMqUser: Arbitrary[RabbitMqUser] = Arbitrary(genRabbitMqUser)

  test("creates should return the appropriate physical resource ID") {
    PropF.forAllF { (input: RabbitMqUser, adminUsername: RabbitMqAdminUsername, adminPassword: RabbitMqAdminPassword) =>
      val client = Client.fromHttpApp(new RabbitMqApi[IO](input.username).rabbitMqApi)

      val secretsManager = new FakeSecretsManagerAlg[IO](adminUsername, adminPassword, input.environment)

      RabbitMqUserResource
        .handleRequest[IO](client, secretsManager)
        .createResource(input)
        .map { output =>
          expect(output.physicalId == PhysicalResourceId.unsafeApply(s"https://${input.host}/api/users/${input.username}"))
        }
    }
  }

  test("deletes should return the appropriate physical resource ID") {
    PropF.forAllF { (input: RabbitMqUser, adminUsername: RabbitMqAdminUsername, adminPassword: RabbitMqAdminPassword) =>
      val client = Client.fromHttpApp(new RabbitMqApi[IO](input.username).rabbitMqApi)
      val secretsManager = new FakeSecretsManagerAlg[IO](adminUsername, adminPassword, input.environment)

      RabbitMqUserResource
        .handleRequest[IO](client, secretsManager)
        .deleteResource(input)
        .map { output =>
          expect(output.physicalId == PhysicalResourceId.unsafeApply(s"https://${input.host}/api/users/${input.username}"))
        }
    }
  }

}

class RabbitMqApi[F[_] : Monad](username: String) extends Http4sDsl[F] {
  private val putUser = HttpRoutes.of[F] {
    case PUT -> Root / "api" / "users" / passedUsername if passedUsername == username =>
      Ok()
    case PUT -> Root / "api" / "users" / _ =>
      BadRequest(json"""{"reason": "wrong username"}""")

    case DELETE -> Root / "api" / "users" / passedUsername if passedUsername == username =>
      Ok()
    case DELETE -> Root / "api" / "users" / _ =>
      BadRequest(json"""{"reason": "wrong username"}""")
  }

  private val putUserPermission = HttpRoutes.of[F] {
    case PUT -> Root / "api" / "permissions" / "/" / passedUsername if passedUsername == username =>
      Ok()
    case PUT -> Root / "api" / "permissions" / "/" / _ =>
      BadRequest()
  }

  val rabbitMqApi: HttpApp[F] = (putUser <+> putUserPermission).orNotFound
}
