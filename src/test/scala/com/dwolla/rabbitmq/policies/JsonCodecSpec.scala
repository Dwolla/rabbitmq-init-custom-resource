package com.dwolla.rabbitmq.policies

import com.dwolla.rabbitmq.Arbitraries
import com.eed3si9n.expecty.Expecty.expect
import io.circe._
import io.circe.literal._
import io.circe.syntax._
import munit.ScalaCheckSuite
import org.scalacheck.Prop


class JsonCodecSpec
  extends ScalaCheckSuite
    with Arbitraries {

  test("RabbitMQ Policy Codec") {
    Prop.forAll { (pattern: String, definitionObj: PolicyDefinition, priority: Option[Int], applyTo: Option[String]) =>
      val input =
        json"""{
                  "pattern": $pattern,
                  "definition": {
                    "ha-mode": ${definitionObj.haMode},
                    "ha-params": ${definitionObj.haParams},
                    "message-ttl": ${definitionObj.messageTtl}
                  },
                  "priority": $priority,
                  "apply-to": $applyTo
               }"""

      val expected = Json.obj(
        "pattern" -> pattern.asJson,
        "definition" -> definitionObj.asJson,
        "priority" -> priority.asJson,
        "apply-to" -> applyTo.asJson,
      )

      expect(input.as[Policy].map(_.asJson) == Right(expected))
    }
  }

}
