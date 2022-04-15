package com.dwolla

import io.circe.{Decoder, Encoder}
import cats.syntax.all._

sealed abstract class DwollaEnvironment(val lowercaseName: String)

object DwollaEnvironment {
  implicit val DwollaEnvironmentEncoder: Encoder[DwollaEnvironment] = Encoder[String].contramap {
    case DevInt => "DevInt"
    case Uat => "Uat"
    case Prod => "Prod"
    case Sandbox => "Sandbox"
  }

  implicit val DwollaEnvironmentDecoder: Decoder[DwollaEnvironment] = Decoder[String].emap {
    case "DevInt" => DevInt.asRight
    case "Uat" => Uat.asRight
    case "Prod" => Prod.asRight
    case "Sandbox" => Sandbox.asRight
    case other => Left(s"'$other' is not a valid Dwolla environment name")
  }
}

case object DevInt extends DwollaEnvironment("devint")
case object Uat extends DwollaEnvironment("uat")
case object Prod extends DwollaEnvironment("prod")
case object Sandbox extends DwollaEnvironment("sandbox")
