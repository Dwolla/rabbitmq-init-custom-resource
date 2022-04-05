package com.dwolla.rabbitmq.policies

import com.dwolla.DwollaEnvironment
import io.circe.generic.extras.Configuration
import io.circe.{Codec, JsonObject}
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.extras.semiauto.deriveConfiguredCodec

case class RabbitMqPolicy(policyName: String,
                          policy: Policy,
                          host: String, // TODO make this Uri?
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
