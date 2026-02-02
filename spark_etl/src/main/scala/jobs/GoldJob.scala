package jobs

import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import cats.effect.IO

object GoldJob {

  /**
   * Main entry point with route filtering
   * @param routeFilter: "10KM", "25KM", or "ALL"
   */
  def run(
    spark: SparkSession,
    silverTables: Map[String, DataFrame],
    routeFilter: String = "ALL"
  ): IO[Unit] = for {
    _ <- IO.println(s"\n🏆 Gold Layer: Processing route=$routeFilter...")

    // Read from Silver S3 output (or use in-memory if available)
    checkins <- routeFilter.toUpperCase match {
      case "10KM" | "25KM" =>
        readSilverData(spark, routeFilter)
      case "ALL" =>
        // Process both routes
        for {
          _ <- processRoute(spark, "10KM")
          _ <- processRoute(spark, "25KM")
        } yield ()
    }

    _ <- IO.println(s"✅ Gold complete for route=$routeFilter\n")
  } yield ()

  /**
   * Process single route
   */
  private def processRoute(spark: SparkSession, route: String): IO[Unit] = for {
    _ <- IO.println(s"  📊 Processing $route...")

    // Read Silver data
    silverPath = s"s3a://tatar-race-data/silver/${route.toLowerCase}"
    checkins <- IO(spark.read.parquet(s"$silverPath/checkins"))

    // Aggregate by BIB
    finalDf <- aggregateByBib(spark, checkins)

    // Write Gold output
    outputPath = s"s3a://tatar-race-data/gold/${route.toLowerCase}"
    _ <- IO {
      finalDf.coalesce(1)
        .write
        .mode("overwrite")
        .json(outputPath)
    }

    count <- IO(finalDf.count())
    _ <- IO.println(s"    ✓ $route: $count runners processed")

  } yield ()

  /**
   * Read Silver data from S3
   */
  private def readSilverData(spark: SparkSession, route: String): IO[DataFrame] = IO {
    val silverPath = s"s3a://tatar-race-data/silver/${route.toLowerCase}/checkins"
    spark.read.parquet(silverPath)
  }

  /**
   * Aggregate checkins by BIB
   */
  private def aggregateByBib(spark: SparkSession, merged: DataFrame): IO[DataFrame] = IO {
    import spark.implicits._

    merged
      .filter($"scannedAt".isNotNull)
      .withColumn("checkpointAt",
        F.date_format(F.from_utc_timestamp($"scannedAt", "Asia/Bangkok"), "yyyy-MM-dd'T'HH:mm:ssXXX")
      )
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
}
