package main

import org.apache.spark.sql.SparkSession
import config.SparkConfig
import spark.SparkSessionManager
import jobs.{BronzeJob, SilverJob, GoldJob}
import cats.effect.{IO, IOApp, ExitCode, Resource}
import cats.implicits._

object Main extends IOApp {

  def formatConfigInfo(config: SparkConfig): String = {
    val env = config.environment.name.toUpperCase().padTo(18, ' ')
    val master = config.master.getOrElse("cluster")
    s"""
       |============================================================
       |  Running: $env
       |============================================================
       |App Name:  ${config.appName}
       |Master:    $master
       |""".stripMargin
  }

  def printConfig(config: SparkConfig): IO[Unit] =
    IO.println(formatConfigInfo(config))

  def sparkResource(config: SparkConfig): Resource[IO, SparkSession] =
    Resource.make(createSparkWithS3Config(config))(spark => IO(spark.stop()))

  def createSparkWithS3Config(config: SparkConfig): IO[SparkSession] = for {
    _ <- SparkSessionManager.configureLogging(config.logLevel)
    builder = SparkSessionManager.buildSessionConfig(config)
      .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      .config("spark.hadoop.fs.s3a.endpoint", "s3.ap-southeast-1.amazonaws.com")
      .config("spark.hadoop.fs.s3a.path.style.access", "true")
      .config("spark.driver.memory", "2g")
      .config("spark.executor.memory", "2g")
      .config("spark.sql.shuffle.partitions", "10")
      .config("spark.sql.ansi.enabled", "false")
    session <- SparkSessionManager.createSession(builder, config.logLevel)
  } yield session

  override def run(args: List[String]): IO[ExitCode] = {
    val config = SparkConfig.load()
    val targetStep = args.headOption.getOrElse("all")

    sparkResource(config).use { spark =>
      for {
        _ <- printConfig(config)
        _ <- IO.println("\n" + "="*60)
        _ <- IO.println(s"Race Data Pipeline - Medallion Architecture [Step: ${targetStep.toUpperCase}]")
        _ <- IO.println("="*60 + "\n")
        _ <- executeStep(spark, targetStep)

        _ <- IO.println("\n" + "="*60)
        _ <- IO.println(s"Step ${targetStep.toUpperCase} Complete Successfully!")
        _ <- IO.println("="*60 + "\n")
      } yield ExitCode.Success
    }.handleErrorWith { error =>
      IO.println(s"\n❌ Pipeline Failed at Step: $targetStep") >>
      IO.println(s"Error Message: ${error.getMessage}") >>
      IO(error.printStackTrace()) >>
      IO.pure(ExitCode.Error)
    }
  }

  def executeStep(spark: SparkSession, step: String): IO[Unit] = step match {
    case "bronze" =>
      BronzeJob.run(spark).void

    case "silver" =>
      for {
        bronze <- BronzeJob.run(spark)
        _      <- SilverJob.run(spark, bronze)
      } yield ()

    case "gold" =>
      for {
        bronze <- BronzeJob.run(spark)
        silver <- SilverJob.run(spark, bronze)
        _      <- GoldJob.run(spark, silver)
      } yield ()

    case _ =>
      for {
        bronze <- BronzeJob.run(spark)
        silver <- SilverJob.run(spark, bronze)
        _      <- GoldJob.run(spark, silver)
      } yield ()
  }
}
