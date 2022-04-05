package com.dwolla

import cats._
import cats.syntax.all._
import com.dwolla.aws.{SecretId, SecretsManagerAlg}
import org.http4s.{BasicCredentials, Uri}
import org.http4s.headers.Authorization

object RabbitMqCommonHandler {
  def rabbitAuthorizationHeader[F[_] : Monad](secretsManagerAlg: SecretsManagerAlg[F])
                                     (env: DwollaEnvironment): F[Authorization] =
    for {
      login_username <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/username"))
      login_password <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/password"))
    } yield Authorization(BasicCredentials(login_username, login_password))

  // TODO move this to JSON decoder
  def baseUri[F[_] : MonadThrow](host: String): F[Uri] =
    Uri.fromString(s"https://$host").liftTo[F]


}
