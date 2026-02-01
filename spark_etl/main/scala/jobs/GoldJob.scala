package jobs

import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import cats.effect.IO

object GoldJob {

  def run(
    spark: SparkSession,
    silverTables: Map[String, DataFrame]
  ): IO[Unit] = for {
    _ <- IO.println("\n🏆 Gold Layer: Formatting Final JSON Output...")

    // ดึง checkins ที่ผ่านการ Merge และ Deduplicate มาแล้วจาก Silver
    checkinsNormalized = silverTables("checkins_normalized")

    _ <- IO.println("  Aggregating by BIB & Formatting Timestamps...")
    finalDf <- aggregateByBib(spark, checkinsNormalized)

    _ <- IO.println("  Writing final output to JSON...")
    _ <- writeGoldJSON(finalDf)

    _ <- IO.println("✅ Gold Complete\n")
  } yield ()

  private def aggregateByBib(spark: SparkSession, merged: DataFrame): IO[DataFrame] = IO {
    import spark.implicits._

    merged
      // กรองเฉพาะที่มีเวลาสแกน (เผื่อกรณี Runner ที่ยังไม่ได้วิ่ง)
      .filter($"scannedAt".isNotNull)
      // แปลงจาก UTC (Silver) เป็น Bangkok (Local) และทำ ISO format
      .withColumn("checkpointAt",
        F.date_format(F.from_utc_timestamp($"scannedAt", "Asia/Bangkok"), "yyyy-MM-dd'T'HH:mm:ssXXX")
      )
      // เรียงลำดับเพื่อให้ checkpoints ใน List เรียงตามความจริง
      .orderBy($"bibNumber", $"sequenceOrder")
      .groupBy($"bibNumber".alias("bib"))
      .agg(
        F.collect_list(
          F.struct(
            $"checkpoint_id".alias("checkpointId"),
            $"checkpointAt"
          )
        ).alias("checkpoints")
      )
  }

  private def writeGoldJSON(df: DataFrame): IO[Unit] = IO {
    val outputPath = "s3a://tatar-race-data/gold/final_checkins_25km"

    df.coalesce(1)
      .write
      .mode("overwrite")
      .json(outputPath)

    println(s"✅ Gold Data written to Private S3: $outputPath")
  }
}
