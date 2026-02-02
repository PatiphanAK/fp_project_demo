package config

import io.github.cdimascio.dotenv.Dotenv
import scala.util.Try

object ConfigLoader {
  type Loader = String => Option[String]

  lazy val dotEnv: Loader =
    Try(Dotenv.configure().ignoreIfMissing().load())
      .toOption
      .fold[Loader](_ => None)(d => k => Option(d.get(k)))

  val system: Loader = k => Option(System.getProperty(k))
  val env: Loader = sys.env.get

  def chain(loaders: Loader*): Loader = k =>
    loaders.iterator.map(_(k)).collectFirst { case Some(v) => v }
}
