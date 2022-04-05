package com.dwolla.rabbitmq
package policies

import com.dwolla.DwollaEnvironment
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, JsonObject}

case class RabbitMqPolicy(policyName: String,
                          policy: Policy,
                          host: UriFromHost,
                          environment: DwollaEnvironment,
                         )

object RabbitMqPolicy {
  implicit val RabbitMqPoliciesCodec: Codec[RabbitMqPolicy] = deriveCodec
}

case class Policy(pattern: String,
                  definition: JsonObject, // TODO should this be more narrowly defined?
                  priority: Option[Int],
                  applyTo: Option[String],
                 )

object Policy {
  implicit val config: Configuration = Configuration.default.withKebabCaseMemberNames
  implicit val PolicyCodec: Codec[Policy] = deriveConfiguredCodec
}
