package config

import org.apache.log4j.Level

case class SparkConfig(
  appName: String,
  master: Option[String],
  logLevel: Level,
  environment: Environment
)

object SparkConfig {

  private object Configs {
    val local: SparkConfig = SparkConfig(
      appName = "Race-Local",
      master = Some("local[*]"),
      logLevel = Level.WARN,
      environment = Environment.Local
    )

    val cloudNative: SparkConfig = SparkConfig(
      appName = "Race-Cloud-Native",
      master = None,
      logLevel = Level.ERROR,
      environment = Environment.CloudNative
    )
  }

  val fromEnvironment: Environment => SparkConfig = {
    case Environment.Local       => Configs.local
    case Environment.CloudNative => Configs.cloudNative
  }

  def load(): SparkConfig =
    fromEnvironment(EnvironmentDetector.fromEnvVar())
}
