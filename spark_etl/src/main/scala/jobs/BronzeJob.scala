  package jobs

  import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
  import cats.effect.IO
  import cats.implicits._
  import domain.SourceConfig
  import java.sql.Timestamp
  import java.time.Instant

  object BronzeJob {

    private val bucket = "tatar-race-data"

    private val sources = List(
      SourceConfig("runners.json", "json", "runners"),
      SourceConfig("routes.json", "json", "routes"),
      SourceConfig("stages.json", "json", "stages"),
      SourceConfig("checkins.json", "json", "checkins"),
      SourceConfig("10k_25k.xlsx", "excel", "excel_25k", Some("25k"))
    )

    // Return Map[tableName -> DataFrame]
    def run(spark: SparkSession): IO[Map[String, DataFrame]] = for {
      _ <- IO.println("\n🔵 Bronze Layer: Loading & Caching Data...")

      tables <- sources.traverse { source =>
        ingestFile(spark, source).map(df => source.targetTable -> df)
      }.map(_.toMap)

      _ <- IO.println(s"✅ Bronze Complete: ${tables.size} tables cached in memory\n")

    } yield tables

    private def ingestFile(spark: SparkSession, config: SourceConfig): IO[DataFrame] = for {
      _ <- IO.println(s"  📥 Loading: ${config.path}")

      rawDf <- readFromS3(spark, config)
      bronzeDf = addMetadata(spark, rawDf, config.path)

      // Cache in memory (IMPORTANT!)
      _ <- IO(bronzeDf.cache())

      // Trigger cache by counting
      count <- IO(bronzeDf.count())

      _ <- IO.println(s"     ✓ Cached: ${config.targetTable} ($count rows)")

    } yield bronzeDf

    private def readFromS3(spark: SparkSession, config: SourceConfig): IO[DataFrame] = IO {
      val path = s"s3a://$bucket/${config.path}"

      config.format.toLowerCase match {
        case "json" =>
          spark.read
            .option("multiline", "true")
            .json(path)

        case "excel" =>
          val reader = spark.read
            .format("com.crealytics.spark.excel")
            .option("header", "true")
            .option("inferSchema", "false")

          config.sheetName match {
            case Some(sheet) =>
              reader.option("dataAddress", s"'$sheet'!A1").load(path)
            case None =>
              reader.load(path)
          }
      }
    }

    private def addMetadata(spark: SparkSession, df: DataFrame, sourceFile: String): DataFrame = {
      import spark.implicits._
      df
        .withColumn("ingested_at", F.lit(Timestamp.from(Instant.now())))
        .withColumn("source_file", F.lit(sourceFile))
        .withColumn("job_id", F.lit(java.util.UUID.randomUUID.toString))
    }
  }
