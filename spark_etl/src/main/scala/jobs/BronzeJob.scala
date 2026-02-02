package jobs

import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import cats.effect.IO
import cats.implicits._
import domain.SourceConfig
import java.sql.Timestamp
import java.time.Instant

object BronzeJob {

  private val S3_BUCKET = "s3a://tatar-race-data"

  /**
   * Run Bronze layer แบบแยก Route
   * @param route: "10KM" หรือ "25KM"
   */
  def run(spark: SparkSession, route: String): IO[Map[String, DataFrame]] = {
    // เลือก Sheet Name และ Target Table Name ตาม Route
    val (excelSheet, targetTableName) = if (route.toUpperCase == "10KM") {
      ("10k", "excel_10k")
    } else {
      ("25k", "excel_25k")
    }

    val sources = List(
      SourceConfig("runners.json", "json", "runners"),
      SourceConfig("routes.json", "json", "routes"),
      SourceConfig("stages.json", "json", "stages"),
      SourceConfig("checkins.json", "json", "checkins"),
      // ✅ แก้ไข: ตั้งชื่อ targetTable ให้ถูกต้อง (excel_10k หรือ excel_25k)
      SourceConfig("10k_25k.xlsx", "excel", targetTableName, Some(excelSheet))
    )

    for {
      _ <- IO.println(s"\n🔵 Bronze Layer: Ingesting Route $route (Sheet: $excelSheet)")

      tables <- sources.traverse { source =>
        ingestFile(spark, source).map(df => source.targetTable -> df)
      }.map(_.toMap)

      _ <- IO.println(s"✅ Bronze Complete: ${tables.size} tables cached")

      // เขียนลง S3 แยก Path ตาม Route
      _ <- writeTablesToS3(spark, tables, route)
    } yield tables
  }

  private def ingestFile(spark: SparkSession, config: SourceConfig): IO[DataFrame] = for {
    rawDf <- readFromS3(spark, config)
    bronzeDf = addMetadata(spark, rawDf, config.path)
    _ <- IO(bronzeDf.cache())
    count <- IO(bronzeDf.count())
    _ <- IO.println(s"     ✓ Cached: ${config.targetTable} ($count rows from ${config.sheetName.getOrElse("file")})")
  } yield bronzeDf

  private def readFromS3(spark: SparkSession, config: SourceConfig): IO[DataFrame] = IO {
    val path = s"$S3_BUCKET/${config.path}"
    config.format.toLowerCase match {
      case "json" => spark.read.option("multiline", "true").json(path)
      case "excel" =>
        spark.read
          .format("com.crealytics.spark.excel")
          .option("header", "true")
          .option("dataAddress", s"'${config.sheetName.get}'!A1")
          .load(path)
    }
  }

  private def addMetadata(spark: SparkSession, df: DataFrame, sourceFile: String): DataFrame = {
    df.withColumn("ingested_at", F.lit(Timestamp.from(Instant.now())))
      .withColumn("source_file", F.lit(sourceFile))
  }

  private def writeTablesToS3(spark: SparkSession, tables: Map[String, DataFrame], route: String): IO[Unit] = IO {
    tables.foreach { case (tableName, df) =>
      val path = s"$S3_BUCKET/bronze/${route.toLowerCase}/$tableName"
      df.write.mode("overwrite").parquet(path)
    }
  }
}
