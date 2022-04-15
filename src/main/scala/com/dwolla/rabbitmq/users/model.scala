package com.dwolla.rabbitmq
package users

import com.dwolla.DwollaEnvironment
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case object MissingLoginUsername extends RuntimeException("Could not find RabbitMQ login username in environment")
case object MissingLoginPassword extends RuntimeException("Could not find RabbitMQ login password in environment")

case class RabbitMqUserDto(password: String, tags: String)
object RabbitMqUserDto {
  implicit val RabbitMqUserDtoCodec: Codec[RabbitMqUserDto] = deriveCodec
}

case class UserCreationException(username: String, reason: String) extends RuntimeException(s"Failed to create user $username: $reason")

case class UserDeletionException(username: String) extends RuntimeException(s"Failed to delete user $username")

case class RabbitMqUser(username: String,
                        password: String,
                        permissions: RabbitMqPermissions,
                        host: UriFromHost,
                        environment: DwollaEnvironment,
                       )

// TODO should we validate these strings (provided by the input) are correct?
case class RabbitMqPermissions(configure: String,
                               write: String,
                               read: String)

object RabbitMqPermissions {
  implicit val RabbitMqPermissionsCodec: Codec[RabbitMqPermissions] = deriveCodec
}

object RabbitMqUser {
  implicit val RabbitMqUserCodec: Codec[RabbitMqUser] = deriveCodec
}
