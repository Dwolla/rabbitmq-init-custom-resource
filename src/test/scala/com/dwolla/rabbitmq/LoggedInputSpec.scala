package com.dwolla.rabbitmq

import cats.effect._
import com.dwolla.Sandbox
import com.dwolla.rabbitmq.RabbitMqCommonHandler.UriFromHost
import com.dwolla.rabbitmq.users.{RabbitMqPermissions, RabbitMqUser}
import feral.lambda.cloudformation.CloudFormationRequestType.CreateRequest
import munit.CatsEffectSuite
import org.http4s.syntax.all._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.testing.TestingLogger

class LoggedInputSpec extends CatsEffectSuite {
  private val passwordMarker = "made-up-password-marker"
  private val user = RabbitMqUser(
    "madeupuser",
    passwordMarker,
    RabbitMqPermissions("made-up-configure", "made-up-write", "made-up-read"),
    UriFromHost(uri"https://made-up-broker-host.example"),
    Sandbox,
  )

  test("a user event is logged at INFO with the user's name, permissions, host and environment, and not the password") {
    val logger = TestingLogger.impl[IO]()
    implicit val l: Logger[IO] = logger

    AbstractCreateOrUpdateHandler.logEvent[IO, RabbitMqUser](user, CreateRequest, None) >>
      logger.logged.map { logged =>
        val messages = logged.collect { case TestingLogger.INFO(message, None) => message }
        assertEquals(messages.length, 1, logged)
        assertEquals(
          List("madeupuser", "made-up-configure", "made-up-write", "made-up-read", "made-up-broker-host.example", "Sandbox").filterNot(messages.head.contains),
          List.empty[String],
        )
        assert(!messages.head.contains(passwordMarker), "the password was logged")
      }
  }
}
