package com.dwolla.rabbitmq

import cats.effect._
import cats.effect.Resource.ExitCase
import cats.syntax.all._
import natchez.{Kernel, Span, TraceValue}

import java.net.URI

/**
 * What a child span recorded by the time it finished: its name, the fields put on it, and the
 * error it finished with, if any.
 */
final case class RecordedSpan(name: String, fields: Map[String, TraceValue], error: Option[Throwable])

/**
 * An in-memory span that appends every child span to `recorded` when the child finishes. Its
 * kernel is one made-up header, so tests can see whether the kernel was propagated.
 */
class RecordingSpan private(recorded: Ref[IO, List[RecordedSpan]], fields: Ref[IO, Map[String, TraceValue]]) extends Span[IO] {
  override def put(fs: (String, TraceValue)*): IO[Unit] = fields.update(_ ++ fs)

  override def kernel: IO[Kernel] = Kernel(Map(RecordingSpan.kernelHeader -> RecordingSpan.kernelValue)).pure[IO]

  override def span(name: String): Resource[IO, Span[IO]] =
    Resource.makeCase(Ref[IO].of(Map.empty[String, TraceValue]).map(childFields => (new RecordingSpan(recorded, childFields), childFields))) {
      case ((_, childFields), exit) =>
        childFields.get.flatMap { fs =>
          recorded.update(_ :+ RecordedSpan(name, fs, errorOf(exit)))
        }
    }.map(_._1)

  override def traceId: IO[Option[String]] = none[String].pure[IO]
  override def spanId: IO[Option[String]] = none[String].pure[IO]
  override def traceUri: IO[Option[URI]] = none[URI].pure[IO]

  private def errorOf(exit: ExitCase): Option[Throwable] =
    exit match {
      case ExitCase.Errored(e) => e.some
      case _ => none
    }
}

object RecordingSpan {
  val kernelHeader = "X-Made-Up-Trace-Kernel"
  val kernelValue = "made-up-kernel-value"

  /**
   * A root span, and the list of child spans recorded under it.
   */
  def root: IO[(Span[IO], IO[List[RecordedSpan]])] =
    (Ref[IO].of(List.empty[RecordedSpan]), Ref[IO].of(Map.empty[String, TraceValue])).mapN { (recorded, fields) =>
      (new RecordingSpan(recorded, fields), recorded.get)
    }
}
