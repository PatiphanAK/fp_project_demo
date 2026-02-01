package spark

import org.apache.spark.sql.SparkSession
import org.apache.log4j.{Level, Logger}
import config.SparkConfig
import cats.effect.IO
import cats.implicits._

object SparkSessionManager {

  // Pure function wrapped in IO monad
  def configureLogging(level: Level): IO[Unit] = IO {
    Logger.getLogger("org").setLevel(level)
    Logger.getLogger("akka").setLevel(level)
  }

  // Pure function - no side effects
  def buildSessionConfig(config: SparkConfig): SparkSession.Builder = {
    val builder = SparkSession.builder().appName(config.appName)
    config.master.fold(builder)(m => builder.master(m))
  }

  // Side effect wrapped in IO
  def createSession(builder: SparkSession.Builder, logLevel: Level): IO[SparkSession] = IO {
    val spark = builder.getOrCreate()
    spark.sparkContext.setLogLevel(logLevel.toString)
    spark
  }

  // Function composition using for-comprehension
  def initialize(config: SparkConfig): IO[SparkSession] = {
    for {
      _       <- configureLogging(config.logLevel)
      builder = buildSessionConfig(config)
      session <- createSession(builder, config.logLevel)
    } yield session
  }

  // Alternative: using applicative style
  def initializeApplicative(config: SparkConfig): IO[SparkSession] = {
    val builder = buildSessionConfig(config)
    configureLogging(config.logLevel) *>
      createSession(builder, config.logLevel)
  }
}
