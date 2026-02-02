package main

import org.apache.spark.sql.{SparkSession, DataFrame}
import config.SparkConfig
import spark.SparkSessionManager
import jobs.{BronzeJob, SilverJob, GoldJob}
import cats.effect.{IO, IOApp, ExitCode, Resource}
import cats.implicits._

object Main extends IOApp {

  private val S3_BUCKET = "s3a://tatar-race-data"

  def formatConfigInfo(config: SparkConfig, step: String, route: String): String = {
    val env = config.environment.name.toUpperCase().padTo(18, ' ')
    val master = config.master.getOrElse("cluster")
    s"""
       |============================================================
       |  Running: $env
       |============================================================
       |App Name:  ${config.appName}
       |Master:    $master
       |Step:      ${step.toUpperCase()}
       |Route:     ${route.toUpperCase()}
       |""".stripMargin
  }

  def printConfig(config: SparkConfig, step: String, route: String): IO[Unit] =
    IO.println(formatConfigInfo(config, step, route))

  def sparkResource(config: SparkConfig): Resource[IO, SparkSession] =
    Resource.make(createSparkWithS3Config(config))(spark => IO(spark.stop()))

  def createSparkWithS3Config(config: SparkConfig): IO[SparkSession] = for {
    _ <- SparkSessionManager.configureLogging(config.logLevel)
    builder = SparkSessionManager.buildSessionConfig(config)
      .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      .config("spark.hadoop.fs.s3a.endpoint", "s3.ap-southeast-1.amazonaws.com")
      .config("spark.hadoop.fs.s3a.path.style.access", "true")
      .config("spark.driver.memory", "1g")
      .config("spark.executor.memory", "2g")
      .config("spark.sql.shuffle.partitions", "10")
      .config("spark.sql.ansi.enabled", "false")
      .config("spark.sql.adaptive.enabled", "true")
    session <- SparkSessionManager.createSession(builder, config.logLevel)
  } yield session

  override def run(args: List[String]): IO[ExitCode] = {
    val config = SparkConfig.load()

    val targetStep = args.headOption.getOrElse("bronze")
    val targetRoute = if (args.length > 1) args(1) else "ALL"

    sparkResource(config).use { spark =>
      for {
        _ <- printConfig(config, targetStep, targetRoute)
        _ <- IO.println("\n" + "="*60)
        _ <- IO.println(s"🚀 Race Data Pipeline - Medallion Architecture")
        _ <- IO.println(s"   Step: ${targetStep.toUpperCase()} | Route: ${targetRoute.toUpperCase()}")
        _ <- IO.println("="*60 + "\n")

        _ <- executeStep(spark, targetStep, targetRoute)

        _ <- IO.println("\n" + "="*60)
        _ <- IO.println(s"✅ Step ${targetStep.toUpperCase()} Complete Successfully!")
        _ <- IO.println("="*60 + "\n")
      } yield ExitCode.Success
    }.handleErrorWith { error =>
      IO.println(s"\n❌ Pipeline Failed at Step: $targetStep") >>
      IO.println(s"   Route: $targetRoute") >>
      IO.println(s"   Error: ${error.getMessage}") >>
      IO(error.printStackTrace()) >>
      IO.pure(ExitCode.Error)
    }
  }

   def executeStep(spark: SparkSession, step: String, route: String): IO[Unit] = step.toLowerCase match {
       case "bronze" =>
         for {
           _ <- BronzeJob.run(spark, route)
           _ <- IO.println(s"✅ Bronze complete for $route")
         } yield ()

       case "silver" =>
         for {
           // ✅ แก้ไข: ส่ง route เข้าไปโหลด Bronze ให้ถูก Folder
           bronzeTables <- loadBronzeFromS3(spark, route)
           _ <- SilverJob.run(spark, bronzeTables, route)
         } yield ()

       case "gold" =>
         executeGold(spark, route)

       case _ => IO.raiseError(new IllegalArgumentException(s"Unknown step: $step"))
     }

  private def executeGold(spark: SparkSession, route: String): IO[Unit] = for {
    _ <- IO.println(s"🏆 Gold Layer: Processing route=$route...")
    silverTables <- loadSilverFromS3(spark, route)
    _ <- GoldJob.run(spark, silverTables, route)
    _ <- IO.println(s"✅ Gold complete for route=$route")
  } yield ()

  /**
   * Load Bronze tables from S3 (Route specific)
   */
  private def loadBronzeFromS3(spark: SparkSession, route: String): IO[Map[String, DataFrame]] = IO {
    // ✅ เลือกชื่อ Table Excel ให้ตรงกับที่ SilverJob คาดหวัง (excel_10k หรือ excel_25k)
    val excelTableName = if (route.toUpperCase == "10KM") "excel_10k" else "excel_25k"

    val tableNames = List(
      "runners",
      "routes",
      "stages",
      "checkins",
      excelTableName // โหลดเฉพาะ Excel ของ Route นั้น
    )

    println(s"📂 Loading Bronze data from S3 (route=$route)...")

    tableNames.map { tableName =>
      // ✅ อ่านจาก Sub-folder ของ Route นั้นๆ
      val path = s"$S3_BUCKET/bronze/${route.toLowerCase}/$tableName"
      println(s"  📥 Reading: $path")

      val df = spark.read.parquet(path)
      tableName -> df
    }.toMap
  }

  private def loadSilverFromS3(spark: SparkSession, route: String): IO[Map[String, DataFrame]] = IO {
    if (route.toUpperCase == "ALL") throw new IllegalArgumentException("Gold layer requires specific route")

    val basePath = s"$S3_BUCKET/silver/${route.toLowerCase}"
    println(s"📂 Loading Silver data from S3 (route=$route)...")

    Map(
      "runners" -> spark.read.parquet(s"$basePath/runners"),
      "checkpoints" -> spark.read.parquet(s"$basePath/checkpoints"),
      "checkins_normalized" -> spark.read.parquet(s"$basePath/checkins")
    )
  }
}
