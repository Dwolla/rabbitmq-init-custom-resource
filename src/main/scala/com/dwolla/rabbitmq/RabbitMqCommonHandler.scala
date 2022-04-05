package com.dwolla.rabbitmq

import cats._
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.dwolla.DwollaEnvironment
import com.dwolla.aws.{SecretId, SecretsManagerAlg}
import io.circe._
import monix.newtypes.NewtypeWrapped
import org.http4s.circe.decodeUri
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, ParseResult, Uri}

object RabbitMqCommonHandler {
  type UriFromHost = UriFromHost.Type
  object UriFromHost extends NewtypeWrapped[Uri] {
    def apply(host: Host): ParseResult[UriFromHost] =
      Uri
        .fromString(s"https://${host.show}")
        .map(UriFromHost(_))

    implicit val UriFromHostnameDecoder: Decoder[UriFromHost] =
      Decoder[Uri]
        .map(UriFromHost(_))
        .or {
          Decoder[Host]
            .emap(UriFromHost(_).leftMap(_.message))
        }

    implicit val UriFromHostnameEncoder: Encoder[UriFromHost] =
      Encoder[String].contramap(_.value.renderString)
  }

  def rabbitAuthorizationHeader[F[_] : Monad](secretsManagerAlg: SecretsManagerAlg[F])
                                             (env: DwollaEnvironment): F[Authorization] =
    for {
      login_username <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/username"))
      login_password <- secretsManagerAlg.getSecret(SecretId(s"rabbitmq/${env.lowercaseName}/rabbitmq/password"))
    } yield Authorization(BasicCredentials(login_username, login_password))
}
