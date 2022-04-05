package com.dwolla.rabbitmq

import com.dwolla.rabbitmq.Arbitraries._
import com.dwolla.{DevInt, DwollaEnvironment, Prod, Sandbox, Uat}
import monix.newtypes.NewtypeWrapped
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

trait Arbitraries {
  val genRabbitMqAdminUsername: Gen[RabbitMqAdminUsername] = Gen.identifier.map(RabbitMqAdminUsername(_))
  implicit val arbRabbitMqAdminUsername: Arbitrary[RabbitMqAdminUsername] = Arbitrary(genRabbitMqAdminUsername)

  val genRabbitMqAdminPassword: Gen[RabbitMqAdminPassword] = arbitrary[String].map(RabbitMqAdminPassword(_))
  implicit val arbRabbitMqAdminPassword: Arbitrary[RabbitMqAdminPassword] = Arbitrary(genRabbitMqAdminPassword)

  val genDwollaEnvironment: Gen[DwollaEnvironment] =
    Gen.oneOf(DevInt, Uat, Prod, Sandbox)
  implicit val arbDwollaEnvironment: Arbitrary[DwollaEnvironment] = Arbitrary(genDwollaEnvironment)
}

object Arbitraries {
  type RabbitMqAdminUsername = RabbitMqAdminUsername.Type
  object RabbitMqAdminUsername extends NewtypeWrapped[String]
  type RabbitMqAdminPassword = RabbitMqAdminPassword.Type
  object RabbitMqAdminPassword extends NewtypeWrapped[String]
}
