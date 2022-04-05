package com.dwolla.rabbitmq.policies

import com.eed3si9n.expecty.Expecty.expect
import io.circe._
import io.circe.literal._
import io.circe.syntax._
import io.circe.testing.instances.arbitraryJsonObject
import munit.ScalaCheckSuite
import org.scalacheck.Prop

class JsonCodecSpec extends ScalaCheckSuite {

  test("RabbitMQ Policy Codec") {
    Prop.forAll { (pattern: String, definitionObj: JsonObject, priority: Option[Int], applyTo: Option[String]) =>
      val input =
        json"""{"pattern": $pattern, "definition": $definitionObj, "priority": $priority, "apply-to": $applyTo}"""

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
