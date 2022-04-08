package com.dwolla.rabbitmq.policies

import com.eed3si9n.expecty.Expecty.expect
import io.circe._
import io.circe.literal._
import io.circe.syntax._
import munit.ScalaCheckSuite
import org.scalacheck.Prop
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}


class JsonCodecSpec extends ScalaCheckSuite {

  private val genPolicyDefinition: Gen[PolicyDefinition] = {

    for {
      `ha-mode` <- arbitrary[String]
      `ha-plan` <- arbitrary[Int]
      `message-ttl` <- arbitrary[Option[Int]]
    } yield PolicyDefinition(`ha-mode`, `ha-plan`, `message-ttl`)
  }
  private implicit val arbPolicyDefinition: Arbitrary[PolicyDefinition] = Arbitrary(genPolicyDefinition)


  test("RabbitMQ Policy Codec") {
    Prop.forAll { (pattern: String, definitionObj: PolicyDefinition, priority: Option[Int], applyTo: Option[String]) =>
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
