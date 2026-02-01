package jobs

import cats.syntax.parallel._
import cats.data.{EitherT, NonEmptyList}
import cats.implicits._
import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import org.apache.spark.sql.expressions.Window
import cats.effect.IO
import domain.{Route10KM, Route25KM}

object SilverJob {

  // Algebraic Data Types for Checkpoint Mapping
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

  def run(spark: SparkSession, bronzeTables: Map[String, DataFrame]): IO[Map[String, DataFrame]] = for {
    _ <- IO.println("\n📷 Silver Layer: Auto-Correcting Cross-Route Scans...")

    // 1. Extract bronze tables
    runners = bronzeTables("runners")
    routes = bronzeTables("routes")
    stages = bronzeTables("stages")
    checkins = bronzeTables("checkins")
    excel10k = bronzeTables.get("excel_10k")
    excel25k = bronzeTables.get("excel_25k")

    // 2. Build unified checkpoint map for ALL routes
    allCheckpointsMap <- buildUnifiedCheckpointMap(spark, stages, routes)

    // 3. Process checkins with auto-correction
    allRunners <- buildAllRunners(spark, runners, routes)
    correctedCheckins <- processCheckinsWithAutoCorrection(
      spark, checkins, allRunners, allCheckpointsMap, excel10k, excel25k
    )

    // 4. Split corrected checkins by route
    checkins10km = correctedCheckins.filter(F.col("corrected_route") === "10KM")
    checkins25km = correctedCheckins.filter(F.col("corrected_route") === "25KM")

    // 5. Build runners and checkpoints per route
    runners10km = allRunners.filter(F.col("route") === "10KM")
    runners25km = allRunners.filter(F.col("route") === "25KM")
    checkpoints10km = allCheckpointsMap.filter(F.col("checkpoint_route") === "10KM")
    checkpoints25km = allCheckpointsMap.filter(F.col("checkpoint_route") === "25KM")

    // 6. Cache
    _ <- IO {
      runners10km.cache()
      runners25km.cache()
      checkpoints10km.cache()
      checkpoints25km.cache()
      checkins10km.cache()
      checkins25km.cache()
    }

    r10Count <- IO(runners10km.count())
    r25Count <- IO(runners25km.count())
    ci10Count <- IO(checkins10km.count())
    ci25Count <- IO(checkins25km.count())
    correctedCount <- IO(correctedCheckins.filter(F.col("was_corrected") === true).count())

    _ <- IO.println(s"  ✓ 10KM: $r10Count runners, $ci10Count checkins")
    _ <- IO.println(s"  ✓ 25KM: $r25Count runners, $ci25Count checkins")
    _ <- IO.println(s"  ✅ Auto-corrected $correctedCount wrong-route scans\n")

  } yield Map(
    "runners_10km" -> runners10km.select("runner_id", "bibNumber", "route"),
    "checkpoints_10km" -> checkpoints10km.select("checkpoint_id", "checkpoint_route", "sequenceOrder", "checkpoint_name", "place_key"),
    "checkins_normalized_10km" -> checkins10km.select("runner_id", "bibNumber", "checkpoint_id", "sequenceOrder", "place_key", "scannedAt", "corrected_route"),
    "runners_25km" -> runners25km.select("runner_id", "bibNumber", "route"),
    "checkpoints_25km" -> checkpoints25km.select("checkpoint_id", "checkpoint_route", "sequenceOrder", "checkpoint_name", "place_key"),
    "checkins_normalized_25km" -> checkins25km.select("runner_id", "bibNumber", "checkpoint_id", "sequenceOrder", "place_key", "scannedAt", "corrected_route")
  )

  /**
   * Build unified checkpoint map covering ALL routes
   * Returns: checkpoint_id -> route mapping
   */
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

  /**
   * Build all runners with their registered routes
   */
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

  /**
   * Process all checkins with automatic route correction
   * If runner scanned wrong route's checkpoint, auto-correct to actual checkpoint's route
   */
  private def processCheckinsWithAutoCorrection(
    spark: SparkSession,
    dbCheckins: DataFrame,
    allRunners: DataFrame,
    allCheckpoints: DataFrame,
    excel10kOpt: Option[DataFrame],
    excel25kOpt: Option[DataFrame]
  ): IO[DataFrame] = IO {
    import spark.implicits._

    // --- 1. Process DB Checkins with Auto-Correction ---
    val dbPart = dbCheckins
      .select($"runner".alias("runner_id"), $"checkpoint".alias("checkpoint_raw"), $"scannedAt")
      .withColumn("scannedAt", F.to_timestamp($"scannedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
      // Join with runners to get registered route
      .join(allRunners.as("r"), Seq("runner_id"), "inner")
      // Join with ALL checkpoints to find actual checkpoint route
      .join(allCheckpoints.as("cp"), $"checkpoint_raw" === $"cp.checkpoint_id", "left")
      // AUTO-CORRECTION LOGIC: Use checkpoint's actual route, not runner's registered route
      .withColumn("corrected_route",
        F.coalesce($"cp.checkpoint_route", $"r.route") // fallback to runner route if checkpoint not found
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
        $"corrected_route", // ใช้ route ที่แก้ไขแล้ว
        $"was_corrected",
        $"correction_note",
        F.lit(0).alias("_prio")
      )

    // --- 2. Process Excel Data (10KM) ---
    val excel10kPart = excel10kOpt.map { excelData =>
      processExcelCheckins(spark, excelData, allRunners, allCheckpoints, "10KM", 1)
    }

    // --- 3. Process Excel Data (25KM) ---
    val excel25kPart = excel25kOpt.map { excelData =>
      processExcelCheckins(spark, excelData, allRunners, allCheckpoints, "25KM", 2)
    }

    // --- 4. Union all sources ---
    val allParts = List(Some(dbPart), excel10kPart, excel25kPart).flatten
    val merged = allParts.reduce(_.unionByName(_))

    // --- 5. Deduplicate (higher prio wins) ---
    val windowSpec = Window.partitionBy("runner_id", "checkpoint_id").orderBy(F.col("_prio").desc)

    val deduped = merged
      .withColumn("rn", F.row_number().over(windowSpec))
      .filter($"rn" === 1)
      .drop("rn", "_prio")

    // --- 6. Log corrections ---
    val corrections = deduped.filter($"was_corrected" === true)
    val correctionCount = corrections.count()

    if (correctionCount > 0) {
      println(s"\n⚡ Auto-corrected $correctionCount wrong-route scans:")
      corrections.select("bibNumber", "checkpoint_id", "correction_note", "corrected_route")
        .show(false)
    }

    // --- 7. Return with all runners (left join to preserve all) ---
    deduped
      .drop("was_corrected", "correction_note")
      .join(
        allRunners.select("runner_id", "bibNumber", "route").as("all"),
        Seq("runner_id", "bibNumber"),
        "right"
      )
      // Use corrected_route, fallback to registered route if no checkins
      .withColumn("corrected_route", F.coalesce($"corrected_route", $"route"))
      .orderBy($"bibNumber", $"sequenceOrder")
  }

  /**
   * Process Excel checkins for specific route
   */
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
      // Join with all checkpoints to get actual route
      .join(allCheckpoints.as("cp"), Seq("place_key"), "inner")
      .select(
        $"r.runner_id",
        $"bibNumber",
        $"cp.checkpoint_id",
        $"cp.sequenceOrder",
        $"cp.place_key",
        $"scannedAt",
        $"cp.checkpoint_route".alias("corrected_route"), // Excel already has correct mapping
        F.lit(priority).alias("_prio")
      )
  }

  /**
   * Get tag to place mapping based on route
   */
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
