package com.dwolla.rabbitmq

import cats._
import cats.syntax.all._
import com.dwolla._
import com.dwolla.aws.{SecretId, SecretsManagerAlg}
import com.dwolla.rabbitmq.Arbitraries._
import io.circe.{Decoder, parser}
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException

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
