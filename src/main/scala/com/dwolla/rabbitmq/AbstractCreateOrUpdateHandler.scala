package com.dwolla.rabbitmq

import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import com.dwolla.rabbitmq.AbstractCreateOrUpdateHandler.logEvent
import feral.lambda.INothing
import feral.lambda.cloudformation.CloudFormationRequestType.{CreateRequest, UpdateRequest}
import feral.lambda.cloudformation.{CloudFormationCustomResource, CloudFormationRequestType, HandlerResponse}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.Logger

// TODO the logging in createResource and updateResource will be a problem if the input is sensitive, like user credentials
// but it seems like we're going to have to move to submitting secret names instead of the
// actual credentials, and retrieving them from Secrets Manager as part of the work this
// lambda does, so it's probably ok for now
abstract class AbstractCreateOrUpdateHandler[F[_] : MonadCancelThrow : Logger, Input : Encoder]
  extends CloudFormationCustomResource[F, Input, INothing] {

  override def createResource(input: Input): F[HandlerResponse[INothing]] =
    logEvent(input, CreateRequest, None) >>
      createOrUpdate(input).guaranteeCase { outcome =>
        logEvent(input, CreateRequest, outcome.some)
      }

  override def updateResource(input: Input): F[HandlerResponse[INothing]] =
    logEvent(input, UpdateRequest, None) >>
      createOrUpdate(input).guaranteeCase { outcome =>
        logEvent(input, UpdateRequest, outcome.some)
      }

  def createOrUpdate(input: Input): F[HandlerResponse[INothing]]
}

object AbstractCreateOrUpdateHandler {
  def logEvent[F[_] : Logger, T : Encoder](input: T, event: CloudFormationRequestType, maybeOutcome: Option[Outcome[F, Throwable, HandlerResponse[INothing]]]): F[Unit] =
    Logger[F].info(
      s"""${maybeOutcome.map(_.toString).getOrElse("starting")} $event:
         |${input.asJson.noSpaces}
         |""".stripMargin)
}
