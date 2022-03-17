package com.dwolla

import cats.effect._
import fs2.Pipe
import io.circe.Json

trait ByteStreamJsonParser[F[_]] {
  def pipe: Pipe[F, Byte, Json]
}

object ByteStreamJsonParser {
  def apply[F[_] : ByteStreamJsonParser]: ByteStreamJsonParser[F] = implicitly

  implicit def byteStreamJsonParserSyncInstance[F[_] : Sync]: ByteStreamJsonParser[F] = new ByteStreamJsonParser[F] {
    override def pipe: Pipe[F, Byte, Json] = _root_.io.circe.fs2.byteStreamParser
  }
}
