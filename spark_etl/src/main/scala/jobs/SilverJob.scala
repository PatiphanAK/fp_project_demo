package jobs

import cats.syntax.parallel._
import cats.data.{EitherT, NonEmptyList}
import cats.implicits._
import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import org.apache.spark.sql.expressions.Window
import cats.effect.IO
import domain.{Route10KM, Route25KM}

object SilverJob {

  sealed trait CheckpointMapping
  case class CorrectRoute(
    checkpointId: String,
    route: String,
    sequenceOrder: Int,
    placeKey: String
  ) extends CheckpointMapping

  case class WrongRouteAutoCorrect(
    checkpointId: String,
    scannedRoute: String,
    actualRoute: String,
    sequenceOrder: Int,
    placeKey: String,
    bibNumber: String
  ) extends CheckpointMapping

  case class CheckpointNotFound(
    checkpointId: String,
    bibNumber: String
  ) extends CheckpointMapping

  /**
   * Main entry point with route filtering
   * @param routeFilter: "10KM", "25KM", or "ALL"
   */
  def run(
    spark: SparkSession,
    bronzeTables: Map[String, DataFrame],
    routeFilter: String = "ALL"
  ): IO[Map[String, DataFrame]] = for {
    _ <- IO.println(s"\n🔷 Silver Layer: Processing route=$routeFilter...")

    // 1. Extract bronze tables
    runners = bronzeTables("runners")
    routes = bronzeTables("routes")
    stages = bronzeTables("stages")
    checkins = bronzeTables("checkins")
    excel10k = bronzeTables.get("excel_10k")
    excel25k = bronzeTables.get("excel_25k")

    // 2. Build unified checkpoint map
    allCheckpointsMap <- buildUnifiedCheckpointMap(spark, stages, routes)

    // 3. Process checkins with auto-correction
    allRunners <- buildAllRunners(spark, runners, routes)
    correctedCheckins <- processCheckinsWithAutoCorrection(
      spark, checkins, allRunners, allCheckpointsMap, excel10k, excel25k
    )

    // 4. Filter by route if needed
    filteredCheckins = routeFilter.toUpperCase match {
      case "10KM" => correctedCheckins.filter(F.col("corrected_route") === "10KM")
      case "25KM" => correctedCheckins.filter(F.col("corrected_route") === "25KM")
      case _ => correctedCheckins
    }

    filteredRunners = routeFilter.toUpperCase match {
      case "10KM" => allRunners.filter(F.col("route") === "10KM")
      case "25KM" => allRunners.filter(F.col("route") === "25KM")
      case _ => allRunners
    }

    filteredCheckpoints = routeFilter.toUpperCase match {
      case "10KM" => allCheckpointsMap.filter(F.col("checkpoint_route") === "10KM")
      case "25KM" => allCheckpointsMap.filter(F.col("checkpoint_route") === "25KM")
      case _ => allCheckpointsMap
    }

    // 5. Cache
    _ <- IO {
      filteredRunners.cache()
      filteredCheckpoints.cache()
      filteredCheckins.cache()
    }

    runnerCount <- IO(filteredRunners.count())
    checkinCount <- IO(filteredCheckins.count())

    _ <- IO.println(s"  ✓ Route=$routeFilter: $runnerCount runners, $checkinCount checkins")

    // 6. Write to S3 (partitioned by route)
    outputPath = s"s3a://tatar-race-data/silver/${routeFilter.toLowerCase}"
    _ <- IO.println(s"  💾 Writing to: $outputPath")

    _ <- IO {
      filteredRunners.write.mode("overwrite").parquet(s"$outputPath/runners")
      filteredCheckpoints.write.mode("overwrite").parquet(s"$outputPath/checkpoints")
      filteredCheckins.write.mode("overwrite").parquet(s"$outputPath/checkins")
    }

    _ <- IO.println(s"  ✅ Silver complete for route=$routeFilter\n")

  } yield Map(
    "runners" -> filteredRunners,
    "checkpoints" -> filteredCheckpoints,
    "checkins_normalized" -> filteredCheckins
  )

  private def buildUnifiedCheckpointMap(
    spark: SparkSession,
    stages: DataFrame,
    routes: DataFrame
  ): IO[DataFrame] = IO {
    import spark.implicits._
    val routeMap = routes.select($"id".alias("route_id"), $"name".alias("route_name"))

    stages
      .join(routeMap, $"route" === $"route_id", "left")
      .filter($"type" === "checkpoint")
      .withColumn("place_key",
        F.when(F.col("name").contains(":"),
          F.trim(F.element_at(F.split(F.col("name"), ":"), -1)))
         .otherwise(F.trim(F.col("name")))
      )
      .select(
        $"id".alias("checkpoint_id"),
        $"route_name".alias("checkpoint_route"),
        $"sequenceOrder",
        $"name".alias("checkpoint_name"),
        $"place_key"
      )
  }

  private def buildAllRunners(
    spark: SparkSession,
    runners: DataFrame,
    routes: DataFrame
  ): IO[DataFrame] = IO {
    import spark.implicits._
    runners
      .withColumn("bibNumber", $"bibNumber".cast("string"))
      .join(
        routes.select($"id".alias("route_id"), $"name".alias("route_name")),
        $"route" === $"route_id",
        "left"
      )
      .select(
        $"id".alias("runner_id"),
        $"bibNumber",
        $"route_name".alias("route")
      )
  }

  private def processCheckinsWithAutoCorrection(
    spark: SparkSession,
    dbCheckins: DataFrame,
    allRunners: DataFrame,
    allCheckpoints: DataFrame,
    excel10kOpt: Option[DataFrame],
    excel25kOpt: Option[DataFrame]
  ): IO[DataFrame] = IO {
    import spark.implicits._

    val dbPart = dbCheckins
      .select($"runner".alias("runner_id"), $"checkpoint".alias("checkpoint_raw"), $"scannedAt")
      .withColumn("scannedAt", F.to_timestamp($"scannedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
      .join(allRunners.as("r"), Seq("runner_id"), "inner")
      .join(allCheckpoints.as("cp"), $"checkpoint_raw" === $"cp.checkpoint_id", "left")
      .withColumn("corrected_route",
        F.coalesce($"cp.checkpoint_route", $"r.route")
      )
      .withColumn("was_corrected",
        F.when($"cp.checkpoint_route".isNotNull && $"cp.checkpoint_route" =!= $"r.route", true)
         .otherwise(false)
      )
      .withColumn("correction_note",
        F.when($"was_corrected" === true,
          F.concat(
            F.lit("CORRECTED:registered="), $"r.route",
            F.lit(",scanned="), $"cp.checkpoint_route",
            F.lit(",checkpoint="), $"cp.checkpoint_name"
          ))
         .otherwise(F.lit(null))
      )
      .select(
        $"runner_id",
        $"bibNumber",
        $"checkpoint_raw".alias("checkpoint_id"),
        $"cp.sequenceOrder",
        $"cp.place_key",
        $"scannedAt",
        $"corrected_route",
        $"was_corrected",
        $"correction_note",
        F.lit(0).alias("_prio")
      )

    val excel10kPart = excel10kOpt.map { excelData =>
      processExcelCheckins(spark, excelData, allRunners, allCheckpoints, "10KM", 1)
    }

    val excel25kPart = excel25kOpt.map { excelData =>
      processExcelCheckins(spark, excelData, allRunners, allCheckpoints, "25KM", 2)
    }

    val allParts = List(Some(dbPart), excel10kPart, excel25kPart).flatten
    val merged = allParts.reduce(_.unionByName(_))

    val windowSpec = Window.partitionBy("runner_id", "checkpoint_id").orderBy(F.col("_prio").desc)

    val deduped = merged
      .withColumn("rn", F.row_number().over(windowSpec))
      .filter($"rn" === 1)
      .drop("rn", "_prio")

    val corrections = deduped.filter($"was_corrected" === true)
    val correctionCount = corrections.count()

    if (correctionCount > 0) {
      println(s"\n⚡ Auto-corrected $correctionCount wrong-route scans:")
      corrections.select("bibNumber", "checkpoint_id", "correction_note", "corrected_route")
        .show(false)
    }

    // Drop temp columns BEFORE final join
    val cleanedDeduped = deduped.drop("was_corrected", "correction_note")

    cleanedDeduped
      .join(
        allRunners.select("runner_id", "bibNumber", "route").as("all"),
        Seq("runner_id", "bibNumber"),
        "right"
      )
      .withColumn("corrected_route", F.coalesce($"corrected_route", $"route"))
      .orderBy($"bibNumber", $"sequenceOrder")
  }

  private def processExcelCheckins(
    spark: SparkSession,
    excelData: DataFrame,
    allRunners: DataFrame,
    allCheckpoints: DataFrame,
    routeName: String,
    priority: Int
  ): DataFrame = {
    import spark.implicits._

    val tagToPlace = getTagToPlaceMapping(routeName)
    val baseTime = "2025-12-06 00:00:00"

    excelData
      .withColumn("bibNumber", F.col("BIB").cast("string"))
      .select($"bibNumber", $"Time", $"Tag")
      .withColumn("place_key", tagToPlace($"Tag"))
      .withColumn("split_time", F.split($"Time", "\\."))
      .withColumn("hours", F.coalesce(F.expr("try_cast(split_time[0] as long)"), F.lit(0L)))
      .withColumn("mins_str", F.coalesce($"split_time".getItem(1), F.lit("00")))
      .withColumn("mins", F.expr("try_cast(rpad(mins_str, 2, '0') as long)"))
      .filter($"mins".isNotNull)
      .withColumn("scannedAt_local",
        F.to_timestamp(F.lit(baseTime)) + F.expr("make_interval(0,0,0,0,hours,mins,0)")
      )
      .withColumn("scannedAt", F.to_utc_timestamp($"scannedAt_local", "Asia/Bangkok"))
      .join(allRunners.as("r"), Seq("bibNumber"), "inner")
      .join(allCheckpoints.as("cp"), Seq("place_key"), "inner")
      .select(
        $"r.runner_id",
        $"bibNumber",
        $"cp.checkpoint_id",
        $"cp.sequenceOrder",
        $"cp.place_key",
        $"scannedAt",
        $"cp.checkpoint_route".alias("corrected_route"),
        F.lit(true).alias("was_corrected"),
        F.lit("Excel Import").alias("correction_note"),
        F.lit(priority).alias("_prio")
      )
  }

  private def getTagToPlaceMapping(routeName: String): org.apache.spark.sql.Column = {
    import org.apache.spark.sql.functions.typedLit

    routeName match {
      case "10KM" => typedLit(Map(
        "CP1" -> "ป่ายูคา",
        "CP6" -> "แปลงปลูกต้นไม้เก่า"
      ))
      case "25KM" => typedLit(Map(
        "CP1" -> "ป่ายูคา",
        "CP2" -> "บ้านยายเสวย",
        "CP3" -> "หมู่บ้านละว้าวังควาย",
        "CP4" -> "บ้านก่อนข้ามกาย",
        "CP5" -> "ร.ร.บ้านดงเสลา",
        "CP6" -> "แปลงปลูกต้นไม้เก่า"
      ))
      case _ => typedLit(Map.empty[String, String])
    }
  }
}
