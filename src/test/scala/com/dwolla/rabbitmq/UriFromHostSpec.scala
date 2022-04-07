package com.dwolla.rabbitmq

import cats.kernel.Eq
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost.hostUriString
import com.eed3si9n.expecty.Expecty.expect
import io.circe.syntax._
import munit.ScalaCheckSuite
import org.http4s.Uri
import org.scalacheck.Prop

class UriFromHostSpec
  extends ScalaCheckSuite
    with Arbitraries {

  test("Host round-trips") {
    Prop.forAll { host: Host =>
      expect(host.asJson.as[Host] === Right(host))
    }
  }

  private implicit val EqThrowable: Eq[Throwable] = Eq.by(_.getMessage)

  test("UriFromHost should prepend https:// to hostnames") {
    Prop.forAll { host: Host =>
      val actual: Either[Throwable, UriFromHost] = host.asJson.as[UriFromHost]
      val expected: Either[Throwable, UriFromHost] = Uri.fromString(s"https://${hostUriString(host)}").map(UriFromHost(_))
      expect(actual === expected)
    }
  }

  test("UriFromHost should accept fully-formed URIs too") {
    Prop.forAll { host: Host =>
      val actual: Either[Throwable, UriFromHost] = s"https://${hostUriString(host)}".asJson.as[UriFromHost]
      val expected: Either[Throwable, UriFromHost] = Uri.fromString(s"https://${hostUriString(host)}").map(UriFromHost(_))
      expect(actual === expected)
    }
  }

}
