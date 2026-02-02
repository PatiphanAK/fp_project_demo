package config

sealed trait Environment {
  def name: String
}

object Environment {
  case object Local extends Environment {
    val name = "Local"
  }

  case object CloudNative extends Environment {
    val name = "Cloud Native"
  }

  def fromString(env: String): Environment = env.toLowerCase match {
    case "cloud-native" | "cloudnative" | "k8s" => CloudNative
    case "local"                               => Local
    case _                                     => CloudNative
  }
}

object EnvironmentDetector {
  type Detector = () => Environment
  val fromEnvVar: Detector = () => {
    val value = sys.env.get("APP_ENV").getOrElse("NOT_FOUND")
    println(s"🔍 DEBUG: Detected APP_ENV from sys.env is: $value")
    Environment.fromString(value)
  }
}
