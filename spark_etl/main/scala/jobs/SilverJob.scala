package jobs

Import cats.syntax.parallel._
import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import org.apache.spark.sql.expressions.Window
import cats.effect.IO
import domain.Route25KM

object SilverJob {

  def run(spark: SparkSession, bronzeTables: Map[String, DataFrame]): IO[Map[String, DataFrame]] = for {
    _ <- IO.println("\n🔷 Silver Layer: Cleaning & Joining Data (Integrated DB + Excel)...")

    // 1. Extract bronze tables
    runners = bronzeTables("runners")
    routes = bronzeTables("routes")
    stages = bronzeTables("stages")
    checkins = bronzeTables("checkins")
    excel25k = bronzeTables("excel_25k")

    // 2. Process Reference Data
    runners25km <- filterRunners25km(spark, runners, routes)
    checkpoints25km <- processCheckpoints(spark, stages, routes)

    // 3. Process & Merge Checkins (DB + Excel)
    checkins25km <- normalizeAndMergeCheckins(spark, checkins, runners25km, checkpoints25km, excel25k)

    // 4. Cache and Count
    _ <- IO {
      runners25km.cache()
      checkpoints25km.cache()
      checkins25km.cache()
    }

    r25Count <- IO(runners25km.count())
    cpCount <- IO(checkpoints25km.count())
    ciCount <- IO(checkins25km.count())

    _ <- IO.println(s"  ✓ Cached: runners_25km ($r25Count rows)")
    _ <- IO.println(s"  ✓ Cached: checkpoints_25km ($cpCount rows)")
    _ <- IO.println(s"  ✓ Cached: checkins_normalized ($ciCount rows)")
    _ <- IO.println(s"✅ Silver Complete: 3 tables cached in memory\n")

  } yield Map(
    "runners_25km" -> runners25km,
    "checkpoints_25km" -> checkpoints25km,
    "checkins_normalized" -> checkins25km
  )

  private def filterRunnersByRoute(
    spark: SparkSession,
    runners: DataFrame,
    routes: DataFrame,
    routeName: String
  ): IO[DataFrame] = IO {
    import spark.implicits._
    runners
      .withColumn("bibNumber", $"bibNumber".cast("string"))
      .join(
        routes.select($"id".alias("route_id"), $"name".alias("route_name")),
        $"route" === $"route_id",
        "left"
      )
      .filter($"route_name" === routeName)
      .select(
        $"id".alias("runner_id"),
        $"bibNumber",
        $"route_name".alias("route")
      )
  }

  private def processCheckpoints(spark: SparkSession, stages: DataFrame, routes: DataFrame): IO[DataFrame] = IO {
    import spark.implicits._
    val routeMap = routes.select($"id".alias("route_id"), $"name".alias("route_name"))

    stages
      .join(routeMap, $"route" === $"route_id", "left")
      .filter($"type" === "checkpoint" && $"route_name" === Route25KM.name)
      .withColumn("place_key",
        F.when(F.col("name").contains(":"),
          F.trim(F.element_at(F.split(F.col("name"), ":"), -1)))
         .otherwise(F.trim(F.col("name")))
      )
      .select(
        $"id".alias("checkpoint_id"),
        $"route_name".alias("route"),
        $"sequenceOrder",
        $"name",
        $"place_key"
      )
      .orderBy($"sequenceOrder")
  }

  private def normalizeAndMergeCheckins(
    spark: SparkSession,
    dbCheckins: DataFrame,
    runners25km: DataFrame,
    checkpoints25km: DataFrame,
    excelData: DataFrame
  ): IO[DataFrame] = IO {
    import spark.implicits._

    // --- 1. Prepare DB Checkins (Prio 0) ---
    val dbPart = dbCheckins
      .select($"runner".alias("runner_id"), $"checkpoint".alias("checkpoint_raw"), $"scannedAt")
      .withColumn("scannedAt", F.to_timestamp($"scannedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
      .join(runners25km.as("r"), Seq("runner_id"), "inner")
      .join(checkpoints25km.as("cp"), $"checkpoint_raw" === $"checkpoint_id", "left")
      .select(
        $"runner_id", $"bibNumber", $"checkpoint_raw".alias("checkpoint_id"),
        $"sequenceOrder", $"place_key", $"scannedAt", $"r.route",
        F.lit(0).alias("_prio")
      )

    // --- 2. Prepare Excel Checkins (Prio 1) ---
    val tagToPlace = F.typedLit(Map(
      "CP1" -> "ป่ายูคา", "CP2" -> "บ้านยายเสวย", "CP3" -> "หมู่บ้านละว้าวังควาย",
      "CP4" -> "บ้านก่อนข้ามฝาย", "CP5" -> "ร.ร.บ้านดงเสลา", "CP6" -> "แปลงปลูกต้นไม้เก่า"
    ))
    val baseTime = "2025-12-06 00:00:00"

    val excelPart = excelData
      // บังคับ BIB เป็น String เพื่อรองรับ VIP1502
      .withColumn("bibNumber", F.col("BIB").cast("string"))
      .select($"bibNumber", $"Time", $"Tag")
      .withColumn("place_key", tagToPlace($"Tag"))
      .withColumn("split_time", F.split($"Time", "\\."))
      // แก้ไข: ใช้ F.expr เพื่อเรียก try_cast ของ Spark SQL โดยตรง
      .withColumn("hours", F.coalesce(F.expr("try_cast(split_time[0] as long)"), F.lit(0L)))
      .withColumn("mins_str", F.coalesce($"split_time".getItem(1), F.lit("00")))
      .withColumn("mins", F.expr("try_cast(rpad(mins_str, 2, '0') as long)"))
      .filter($"mins".isNotNull)
      // Calculate Time: Local -> UTC
      .withColumn("scannedAt_local",
        F.to_timestamp(F.lit(baseTime)) + F.expr("make_interval(0,0,0,0,hours,mins,0)")
      )
      .withColumn("scannedAt", F.to_utc_timestamp($"scannedAt_local", "Asia/Bangkok"))
      // Join โดยใช้ String ทั้งคู่ (จบปัญหา Shuffle Error)
      .join(runners25km.as("r"), Seq("bibNumber"), "inner")
      .join(checkpoints25km.as("cp"), Seq("place_key"), "inner")
      .select(
        $"r.runner_id", $"bibNumber", $"cp.checkpoint_id",
        $"cp.sequenceOrder", $"cp.place_key", $"scannedAt", $"r.route",
        F.lit(1).alias("_prio")
      )

    // --- 3. Merge & Deduplicate (Excel wins over DB) ---
    val windowSpec = Window.partitionBy("runner_id", "checkpoint_id").orderBy($"_prio".desc)

    dbPart.unionByName(excelPart)
      .withColumn("rn", F.row_number().over(windowSpec))
      .filter($"rn" === 1)
      .drop("rn", "_prio")
      .join(runners25km.select("runner_id", "bibNumber", "route").as("all"),
            Seq("runner_id", "bibNumber", "route"), "right")
      .orderBy($"bibNumber", $"sequenceOrder")
  }
}
