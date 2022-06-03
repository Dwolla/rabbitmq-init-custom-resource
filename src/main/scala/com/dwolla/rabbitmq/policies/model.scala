package com.dwolla.rabbitmq
package policies

import com.dwolla.DwollaEnvironment
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec}

case class RabbitMqPolicy(policyName: String,
                          policy: Policy,
                          host: UriFromHost,
                          environment: DwollaEnvironment,
                         )

object RabbitMqPolicy {
  implicit val RabbitMqPoliciesCodec: Codec[RabbitMqPolicy] = deriveCodec
}

case class Policy(pattern: String,
                  definition: PolicyDefinition,
                  priority: Option[Int],
                  applyTo: Option[String],
                 )

case class PolicyDefinition(haMode: String,
                            haParams: Option[Int],
                            haSyncMode: String,
                            messageTtl: Option[Int])

object PolicyDefinition {
  @scala.annotation.nowarn("msg=private val config in object PolicyDefinition is never used")
  private implicit val config: Configuration = Configuration.default.withKebabCaseMemberNames
  implicit val PolicyDefinitionCodec: Codec[PolicyDefinition] = deriveConfiguredCodec
}

object Policy {
  implicit val config: Configuration = Configuration.default.withKebabCaseMemberNames
  implicit val PolicyCodec: Codec[Policy] = deriveConfiguredCodec
}
