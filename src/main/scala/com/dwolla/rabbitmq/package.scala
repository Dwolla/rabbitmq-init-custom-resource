package com.dwolla

import cats.syntax.all._
import com.comcast.ip4s.Host
import io.circe._

package object rabbitmq {
  implicit val hostDecoder: Decoder[Host] = Decoder[String].emap {
    Host
      .fromString(_)
      .toRight("Could not decode the value as a host")
  }
  implicit val hostEncoder: Encoder[Host] = Encoder[String].contramap(_.show)
}
