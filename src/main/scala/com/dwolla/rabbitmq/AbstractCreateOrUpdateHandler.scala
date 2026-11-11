package com.dwolla.rabbitmq

import cats.Show
import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import com.dwolla.rabbitmq.AbstractCreateOrUpdateHandler.logEvent
import feral.lambda.INothing
import feral.lambda.cloudformation.CloudFormationRequestType.{CreateRequest, UpdateRequest}
import feral.lambda.cloudformation.{CloudFormationCustomResource, CloudFormationRequestType, HandlerResponse}
import org.typelevel.log4cats.Logger

/**
 * Logs each create and update event, and its outcome, with the input as its `Show` instance
 * renders it.
 */
abstract class AbstractCreateOrUpdateHandler[F[_] : MonadCancelThrow : Logger, Input : Show]
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
  def logEvent[F[_] : Logger, T : Show](input: T, event: CloudFormationRequestType, maybeOutcome: Option[Outcome[F, Throwable, HandlerResponse[INothing]]]): F[Unit] =
    Logger[F].info(
      s"""${maybeOutcome.map(_.toString).getOrElse("starting")} $event:
         |${input.show}
         |""".stripMargin)
}
