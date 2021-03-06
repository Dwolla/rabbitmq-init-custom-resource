package com.dwolla.rabbitmq

import com.dwolla.rabbitmq.policies.PolicyDefinition
import com.comcast.ip4s.Arbitraries._
import com.comcast.ip4s.Host
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

  val genHost: Gen[Host] = Gen.oneOf(ipGenerator, hostnameGenerator) // TODO add idnGenerator
  implicit val arbHost: Arbitrary[Host] = Arbitrary(genHost)

  val genPolicyDefinition: Gen[PolicyDefinition] =
    for {
      haMode <- arbitrary[String]
      haParams <- arbitrary[Option[Int]]
      haSyncMode <- arbitrary[String]
      messageTtl <- arbitrary[Option[Int]]
    } yield PolicyDefinition(haMode, haParams, haSyncMode, messageTtl)
  implicit val arbPolicyDefinition: Arbitrary[PolicyDefinition] = Arbitrary(genPolicyDefinition)
}

object Arbitraries {
  type RabbitMqAdminUsername = RabbitMqAdminUsername.Type
  object RabbitMqAdminUsername extends NewtypeWrapped[String]
  type RabbitMqAdminPassword = RabbitMqAdminPassword.Type
  object RabbitMqAdminPassword extends NewtypeWrapped[String]
}
