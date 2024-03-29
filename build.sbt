ThisBuild / organization := "com.dwolla"
ThisBuild / description := "CloudFormation custom resources for initializing RabbitMQ users and policies"
ThisBuild / homepage := Some(url("https://github.com/Dwolla/rabbitmq-init-custom-resource"))
ThisBuild / licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
ThisBuild / scalaVersion := "2.13.9"
ThisBuild / developers := List(
  Developer(
    "bpholt",
    "Brian Holt",
    "bholt+rabbitmq-init-custom-resource@dwolla.com",
    url("https://dwolla.com")
  ),
)
ThisBuild / startYear := Option(2021)
ThisBuild / libraryDependencies ++= Seq(
  compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("8"), JavaSpec.temurin("11"))
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq.empty
ThisBuild / githubWorkflowPublish := Seq.empty
ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "doc", "Universal / packageBin")))

lazy val `rabbitmq-init-custom-resource` = (project in file("."))
  .settings(
    maintainer := developers.value.head.email,
    topLevelDirectory := None,
    libraryDependencies ++= {
      val natchezVersion = "0.1.6"
      val feralVersion = "0.1.0-M5"
      val munitVersion = "0.7.29"
      val circeVersion = "0.14.1"
      val scalacheckEffectVersion = "1.0.3"
      val log4catsVersion = "2.2.0"
      val monocleVersion = "2.1.0"
      val http4sVersion = "0.23.11"
      val awsSdkVersion = "2.17.143"
      val refinedV = "0.9.28"

      Seq(
        "org.typelevel" %% "feral-lambda-cloudformation-custom-resource" % feralVersion,
        "org.tpolecat" %% "natchez-xray" % natchezVersion,
        "org.tpolecat" %% "natchez-http4s" % "0.3.2",
        "org.typelevel" %% "cats-tagless-macros" % "0.14.0",
        "org.http4s" %% "http4s-ember-client" % http4sVersion,
        "io.circe" %% "circe-parser" % circeVersion,
        "io.circe" %% "circe-generic" % circeVersion,
        "io.circe" %% "circe-generic-extras" % circeVersion,
        "io.circe" %% "circe-optics" % circeVersion,
        "io.circe" %% "circe-fs2" % "0.14.0",
        "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
        "com.amazonaws" % "aws-lambda-java-log4j2" % "1.5.1" % Runtime,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.1" % Runtime,
        "com.chuusai" %% "shapeless" % "2.3.8",
        "com.dwolla" %% "fs2-aws-java-sdk2" % "3.0.0-RC1",
        "software.amazon.awssdk" % "secretsmanager" % awsSdkVersion,
        "io.circe" %% "circe-literal" % circeVersion % Test,
        "org.scalameta" %% "munit" % munitVersion % Test,
        "org.scalameta" %% "munit-scalacheck" % munitVersion % Test,
        "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
        "org.typelevel" %% "scalacheck-effect" % scalacheckEffectVersion % Test,
        "org.typelevel" %% "scalacheck-effect-munit" % scalacheckEffectVersion % Test,
        "org.typelevel" %% "log4cats-noop" % log4catsVersion % Test,
        "io.circe" %% "circe-testing" % circeVersion % Test,
        "com.github.julien-truffaut" %% "monocle-core" % monocleVersion % Test,
        "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion % Test,
        "org.http4s" %% "http4s-dsl" % http4sVersion % Test,
        "org.http4s" %% "http4s-laws" % http4sVersion % Test,
        "com.comcast" %% "ip4s-test-kit" % "3.1.2" % Test,
        "com.eed3si9n.expecty" %% "expecty" % "0.15.4" % Test,
        "software.amazon.awssdk" % "sts" % awsSdkVersion % Test,
        "eu.timepit" %% "refined-scalacheck" % refinedV % Test,
        "org.typelevel" %% "cats-laws" % "2.7.0" % Test,
        "org.typelevel" %% "discipline-munit" % "1.0.9" % Test,
      )
    },
    addBuildInfoToConfig(Test),
  )
  .enablePlugins(
    UniversalPlugin,
    JavaAppPackaging,
    BuildInfoPlugin,
    ServerlessDeployPlugin,
  )
