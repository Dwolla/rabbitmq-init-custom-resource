package com.dwolla.rabbitmq.users

import cats._
import cats.effect._
import cats.syntax.all._
import com.dwolla._
import com.dwolla.aws.{SecretId, SecretsManagerAlg}
import com.dwolla.rabbitmq.users.HandlerSpec.{RabbitMqAdminPassword, RabbitMqAdminUsername}
import com.eed3si9n.expecty.Expecty.expect
import feral.lambda.cloudformation.PhysicalResourceId
import io.circe.literal.JsonStringContext
import io.circe.{Decoder, parser}
import monix.newtypes.NewtypeWrapped
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.circe.jsonEncoder
import org.http4s.client._
import org.http4s.dsl.Http4sDsl
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF
import org.scalacheck.{Arbitrary, Gen}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException

object HandlerSpec {
  type RabbitMqAdminUsername = RabbitMqAdminUsername.Type
  object RabbitMqAdminUsername extends NewtypeWrapped[String]
  type RabbitMqAdminPassword = RabbitMqAdminPassword.Type
  object RabbitMqAdminPassword extends NewtypeWrapped[String]
}

class HandlerSpec
  extends CatsEffectSuite
    with ScalaCheckEffectSuite {

  private implicit val logger: Logger[IO] = NoOpLogger[IO]

  private val genRabbitMqAdminUsername: Gen[RabbitMqAdminUsername] = Gen.identifier.map(RabbitMqAdminUsername(_))
  private implicit val arbRabbitMqAdminUsername: Arbitrary[RabbitMqAdminUsername] = Arbitrary(genRabbitMqAdminUsername)

  private val genRabbitMqAdminPassword: Gen[RabbitMqAdminPassword] = arbitrary[String].map(RabbitMqAdminPassword(_))
  private implicit val arbRabbitMqAdminPassword: Arbitrary[RabbitMqAdminPassword] = Arbitrary(genRabbitMqAdminPassword)

  private val genRabbitMqPermissions: Gen[RabbitMqPermissions] = {
    val genPermission = Gen.oneOf("^$", ".*")

    for {
      configure <- genPermission
      write <- genPermission
      read <- genPermission
    } yield RabbitMqPermissions(configure, write, read)
  }
  private implicit val arbRabbitMqPermissions: Arbitrary[RabbitMqPermissions] = Arbitrary(genRabbitMqPermissions)

  private val genDwollaEnvironment: Gen[DwollaEnvironment] =
    Gen.oneOf(DevInt, Uat, Prod, Sandbox)
  private implicit val arbDwollaEnvironment: Arbitrary[DwollaEnvironment] = Arbitrary(genDwollaEnvironment)

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

class FakeSecretsManagerAlg[F[_] : MonadThrow](adminUsername: RabbitMqAdminUsername, adminPassword: RabbitMqAdminPassword, env: DwollaEnvironment) extends SecretsManagerAlg[F] {
  override def getSecret(secretId: SecretId): F[String] =
    if (secretId.value == s"rabbitmq/${env.lowercaseName}/rabbitmq/username") adminUsername.value.pure[F]
    else if (secretId.value == s"rabbitmq/${env.lowercaseName}/rabbitmq/password") adminPassword.value.pure[F]
    else ResourceNotFoundException.builder().message(s"could not find ${secretId.value}").build().raiseError

  override def getSecretAs[A: Decoder](secretId: SecretId): F[A] =
    for {
      secretString <- getSecret(secretId)
      secretJson <- parser.parse(secretString).liftTo[F]
      a <- secretJson.as[A].liftTo[F]
    } yield a
}
