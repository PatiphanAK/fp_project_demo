# Apache Spark & Hadoop

> Big Picture ว่าสองเครื่องมือนี้คืออะไร และทำงานร่วมกันยังไง

---

## ปัญหาที่ Spark และ Hadoop แก้

ลองนึกถึงสถานการณ์นี้:

> มีข้อมูล Log ขนาด **10 TB** และต้องการนับว่า User แต่ละคน Login กี่ครั้งในเดือนที่ผ่านมา

ถ้าใช้คอมพิวเตอร์เครื่องเดียว:
- ข้อมูล 10 TB ไม่พอดี RAM (RAM ทั่วไปมีแค่ 16-64 GB)
- แม้อ่านจาก Disk ได้ก็จะช้ามาก
- ประมวลผลอาจใช้เวลาหลายวัน

**วิธีแก้:** กระจายงานไปหลายเครื่องพร้อมกัน (Distributed Computing)

```
ข้อมูล 10 TB
     │
     ├──► เครื่องที่ 1: ประมวลผล 2.5 TB
     ├──► เครื่องที่ 2: ประมวลผล 2.5 TB
     ├──► เครื่องที่ 3: ประมวลผล 2.5 TB
     └──► เครื่องที่ 4: ประมวลผล 2.5 TB
               │
               ▼
         รวมผลลัพธ์
```

นี่คือหัวใจของทั้ง Hadoop และ Spark

---

## Hadoop คืออะไร?

![Hadoop](https://www.apache.org/logos/res/hadoop/default.png)
**Apache Hadoop** คือ Framework แรก ๆ ที่ทำให้ Distributed Computing เข้าถึงได้ง่าย ประกอบด้วย 2 ส่วนหลัก:

### HDFS — ระบบเก็บข้อมูลแบบกระจาย

**HDFS (Hadoop Distributed File System)** คือระบบไฟล์ที่แบ่งไฟล์ขนาดใหญ่ออกเป็น **Block** (ปกติ 128MB) แล้วกระจายไปเก็บหลายเครื่อง พร้อมทำสำเนาสำรอง (Replication) เผื่อเครื่องเสีย

```
ไฟล์ขนาด 512 MB
     │
     ├── Block 1 (128MB) → Node A, Node B (สำรอง)
     ├── Block 2 (128MB) → Node B, Node C (สำรอง)
     ├── Block 3 (128MB) → Node C, Node A (สำรอง)
     └── Block 4 (128MB) → Node A, Node D (สำรอง)
```

### MapReduce — วิธีประมวลผลแบบ Hadoop

MapReduce คือ Programming Model ที่แบ่งงานเป็น 2 Phase:
- **Map:** แปลงข้อมูลแต่ละ Record → Key-Value pairs
- **Reduce:** รวม Value ที่มี Key เดียวกัน

**ข้อเสียหลักของ MapReduce:** ทุก Step ต้องเขียน-อ่าน Disk → ช้ามากเมื่อมีหลาย Step

---

## Apache Spark คืออะไร?
![ApacheSpark](https://upload.wikimedia.org/wikipedia/commons/thumb/f/f3/Apache_Spark_logo.svg/3840px-Apache_Spark_logo.svg.png)
**Apache Spark** คือ Distributed Computing Engine รุ่นใหม่ที่แก้ข้อเสียของ MapReduce โดยประมวลผลใน **RAM (In-Memory)** เป็นหลัก ทำให้เร็วกว่า MapReduce ได้ถึง 100 เท่าในบางกรณี

### Spark กับ Hadoop — ต่างกันยังไง?

| | Hadoop MapReduce | Apache Spark |
|--|-----------------|--------------|
| **เก็บข้อมูลระหว่าง Step** | Disk | RAM (In-Memory) |
| **ความเร็ว** | ช้า (Disk I/O มาก) | เร็วกว่ามาก |
| **API** | Low-level, ยากเขียน | High-level (DataFrame, SQL) |
| **Streaming** | ไม่รองรับ | รองรับ (Structured Streaming) |
| **ML** | ต้องใช้เครื่องมืออื่น | มี MLlib ในตัว |

> **Spark ไม่ได้แทนที่ Hadoop ทั้งหมด** — Spark มักรันบน HDFS เพื่อเก็บข้อมูล แต่ประมวลผลใน Memory แทน

---

## โครงสร้างของ Spark

```
┌─────────────────────────────────────────┐
│             Driver Program              │
│  (โปรแกรม Scala/Python ที่เราเขียน)    │
│                                         │
│  SparkContext / SparkSession            │
└──────────────┬──────────────────────────┘
               │ สั่งงาน
               ▼
┌─────────────────────────────────────────┐
│            Cluster Manager              │
│  (Kubernetes / YARN / Standalone)       │
└──────┬──────────────┬───────────────────┘
       │              │
       ▼              ▼
┌──────────┐    ┌──────────┐
│ Executor │    │ Executor │  ... (หลายตัว)
│  Node 1  │    │  Node 2  │
│ Task Task│    │ Task Task│
└──────────┘    └──────────┘
```

- **Driver:** โปรแกรมหลักที่เราเขียน — วางแผนว่าจะทำอะไร
- **Executor:** Worker ที่ทำงานจริง — รับ Task จาก Driver มารัน
- **Task:** งานชิ้นเล็ก ๆ ที่แบ่งจาก Operation ใหญ่

---

## Spark บน Kubernetes

เมื่อรัน Spark บน Kubernetes ผ่าน Spark Operator:

```
SparkApplication (YAML)
        │
        ▼
  Spark Operator
        │
        ├──► Spark Driver Pod   (โปรแกรม Driver รันที่นี่)
        │
        └──► Spark Executor Pods (Worker หลายตัว)
```

ข้อดีของการรันบน Kubernetes:
- ใช้ Infrastructure เดียวกับ Service อื่น ๆ
- Scale Executor ขึ้น-ลงได้อัตโนมัติ
- จัดการ Resource ด้วย Kubernetes Resource Limits

---

## Lazy Evaluation — แนวคิดสำคัญของ Spark

Spark ไม่ได้รัน Operation ทันทีที่เรียก แต่จะรอจนกว่าจะมีการขอ **ผลลัพธ์จริง** (เรียกว่า Action)

```
val df = spark.read.csv("data.csv")   // ยังไม่อ่านไฟล์
  .filter(col("age") > 18)            // ยังไม่ Filter
  .groupBy("city")                    // ยังไม่ Group
  .count()                            // ยังไม่นับ

df.show()  // ← ตรงนี้แหละที่ Spark เริ่มทำงานจริง!
```

ข้อดีของ Lazy Evaluation: Spark สามารถ **วางแผน Optimize** การรันทั้งหมดก่อน แล้วรันได้อย่างมีประสิทธิภาพสูงสุด

---
# DataFrame

> ตารางข้อมูลแบบกระจาย — หัวใจของ Spark SQL API

---

## DataFrame คืออะไร?

ลองนึกถึง **ตาราง Excel** ที่:
- มีชื่อ Column และ Type ชัดเจน (Schema)
- สามารถกระจายไปอยู่หลายเครื่องได้โดยอัตโนมัติ
- รองรับข้อมูลหลายพัน ล้าน แถว โดยไม่ต้องแก้ Code

```
┌──────────┬─────┬────────┐
│   name   │ age │  city  │
├──────────┼─────┼────────┤
│  Alice   │  28 │Bangkok │  ← อยู่ Node 1
│  Bob     │  35 │Chiang  │  ← อยู่ Node 1
│  Charlie │  22 │Phuket  │  ← อยู่ Node 2
│  Diana   │  41 │Bangkok │  ← อยู่ Node 2
└──────────┴─────┴────────┘
       Spark DataFrame
```

**สำคัญ:** ใน Code เราเห็นเป็นตารางเดียว แต่ Spark กระจายข้อมูลอยู่จริง ๆ

---

## DataFrame vs RDD

Spark มี 2 abstraction หลัก:

| | RDD | DataFrame |
|--|-----|-----------|
| **ระดับ** | Low-level | High-level |
| **Type Safety** | ✅ (Compile-time) | ⚠️ (Runtime) |
| **Optimization** | ❌ ต้องทำเอง | ✅ Catalyst Optimizer ช่วย |
| **ใช้งานง่าย** | ❌ Verbose | ✅ คล้าย SQL |
| **แนะนำสำหรับ** | งานที่ต้องควบคุม Low-level | งาน ETL ทั่วไป |

> **Dataset[T]** คือ hybrid ระหว่างสอง — มี Schema เหมือน DataFrame แต่ Type-safe เหมือน RDD ใช้ใน Scala เป็นหลัก

---

## SparkSession — จุดเริ่มต้นทุกอย่าง

```scala
import org.apache.spark.sql.SparkSession

val spark = SparkSession.builder()
  .appName("MySparkJob")
  .master("local[*]")   // รันบนเครื่องเดียว (ใช้ทุก CPU Core)
  .getOrCreate()

import spark.implicits._  // ให้ใช้ .toDF() และ $ syntax ได้
```

---

## การสร้าง DataFrame

### จาก Collection (สำหรับ Test)

```scala
val data = Seq(
  ("Alice",   28, "Bangkok"),
  ("Bob",     35, "Chiang Mai"),
  ("Charlie", 22, "Phuket"),
  ("Diana",   41, "Bangkok")
)

val df = data.toDF("name", "age", "city")

df.printSchema()
// root
//  |-- name: string (nullable = true)
//  |-- age: integer (nullable = false)
//  |-- city: string (nullable = true)
```

### จากไฟล์ CSV

```scala
val df = spark.read
  .option("header", "true")      // บรรทัดแรกเป็น Header
  .option("inferSchema", "true") // ให้ Spark เดา Type เอง
  .csv("data/users.csv")
```

### จากไฟล์ Parquet (แนะนำสำหรับ Production)

```scala
val df = spark.read.parquet("data/users.parquet")
```

> **Parquet** คือ Columnar Format — อ่านเร็วกว่า CSV มากเมื่อต้องการแค่บาง Column

---

## Transformation — แปลงข้อมูล

Transformation ทุกตัวคือ **Lazy** (ดูอธิบายใน spark-hadoop.md)

### filter / where

```scala
// กรองเฉพาะคนที่อายุ > 25
val adults = df.filter(col("age") > 25)

// เขียนแบบ SQL-like ได้เหมือนกัน
val adults = df.where("age > 25")
```

### select และ withColumn

```scala
import org.apache.spark.sql.functions._

// เลือกแค่บาง Column
val names = df.select("name", "city")

// เพิ่ม Column ใหม่
val withFullInfo = df.withColumn(
  "is_adult",
  col("age") >= 18
)

// คำนวณ Column ใหม่
val withLabel = df.withColumn(
  "age_group",
  when(col("age") < 30, "young")
    .when(col("age") < 50, "middle")
    .otherwise("senior")
)
```

### groupBy และ Aggregation

```scala
// นับจำนวนคนต่อเมือง
val byCity = df
  .groupBy("city")
  .agg(
    count("*").as("total"),
    avg("age").as("avg_age"),
    max("age").as("max_age")
  )

byCity.show()
// +----------+-----+-------+-------+
// |      city|total|avg_age|max_age|
// +----------+-----+-------+-------+
// |   Bangkok|    2|   34.5|     41|
// |Chiang Mai|    1|   35.0|     35|
// |    Phuket|    1|   22.0|     22|
// +----------+-----+-------+-------+
```

### join

```scala
val orders = spark.read.parquet("data/orders.parquet")

// Inner Join
val result = df.join(orders, df("name") === orders("user_name"))

// Left Join
val result = df.join(orders, df("name") === orders("user_name"), "left")
```

---

## Action — บังคับให้ Spark รันจริง

```scala
// แสดงผลลัพธ์ (default 20 แถว)
df.show()
df.show(50)        // แสดง 50 แถว
df.show(false)     // ไม่ตัดข้อความยาว

// เก็บไว้ใน Memory (ไม่แนะนำกับข้อมูลใหญ่มาก)
val rows: Array[Row] = df.collect()

// นับจำนวนแถว
val count: Long = df.count()

// บันทึกออกไป (คำสั่ง write เป็น Action)
df.write
  .mode("overwrite")
  .parquet("output/result.parquet")
```

---

## ตัวอย่าง Pipeline จริง

```scala
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object UserAnalysis {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("UserAnalysis")
      .getOrCreate()

    import spark.implicits._

    // 1. อ่านข้อมูล
    val users = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv("data/users.csv")

    // 2. ทำความสะอาด
    val cleaned = users
      .filter(col("age").isNotNull)
      .filter(col("age") > 0 && col("age") < 120)
      .withColumn("city", trim(lower(col("city"))))

    // 3. วิเคราะห์
    val summary = cleaned
      .groupBy("city")
      .agg(
        count("*").as("user_count"),
        round(avg("age"), 1).as("avg_age")
      )
      .orderBy(desc("user_count"))

    // 4. บันทึก
    summary.write
      .mode("overwrite")
      .parquet("output/city_summary")

    spark.stop()
  }
}
```
# Cats Effect

> IO Monad, Fiber, และ Resource Management สำหรับ Functional Programming ใน Scala

---

## ปัญหาที่ Cats Effect แก้

ใน FP บริสุทธิ์ (Pure FP) ฟังก์ชันต้องเป็น **Pure** — เรียกกี่ครั้งก็ได้ผลเหมือนเดิม ไม่มี Side Effect

แต่โปรแกรมจริง ๆ ต้องทำสิ่งเหล่านี้:
- อ่าน/เขียนไฟล์
- เรียก Network
- อ่าน System Clock
- Print ข้อมูล

สิ่งเหล่านี้ล้วนเป็น **Side Effect** ทั้งนั้น

**คำถาม:** จะเขียน Pure FP แต่ยังทำ Side Effect ได้ยังไง?

**คำตอบ:** ห่อ Side Effect ไว้ใน **IO** แทนที่จะรันทันที

---

## IO Monad คืออะไร?

`IO[A]` ไม่ใช่ผลลัพธ์ — มันคือ **คำอธิบาย** ว่าจะทำอะไร

เปรียบเหมือน **ใบสั่งอาหาร** กับ **อาหารจริง**:

```
val recipe: IO[Cake] = IO {
  mixIngredients()
  bakeInOven()
  Cake()
}
// ณ จุดนี้ยังไม่มีการผสมหรืออบอะไรเลย
// recipe แค่ "อธิบาย" ว่าจะทำอะไร

recipe.unsafeRunSync()
// ตรงนี้แหละที่ "ทำจริง"
```

---

## IO พื้นฐาน

```scala
import cats.effect.IO

// สร้าง IO จาก Side Effect
val printHello: IO[Unit] = IO.println("Hello, World!")

// สร้าง IO ที่ return ค่า
val readLine: IO[String] = IO.readLine

// IO ที่ return ค่าคงที่ (ไม่มี Side Effect จริง)
val pure: IO[Int] = IO.pure(42)

// IO ที่ Delay การรัน (Lazy)
val delayed: IO[Long] = IO(System.currentTimeMillis())
```

### การต่อ IO ด้วย flatMap และ for-comprehension

```scala
// แบบ flatMap
val program: IO[Unit] =
  IO.println("What's your name?")
    .flatMap(_ => IO.readLine)
    .flatMap(name => IO.println(s"Hello, $name!"))

// แบบ for-comprehension (อ่านง่ายกว่า)
val program: IO[Unit] = for {
  _    <- IO.println("What's your name?")
  name <- IO.readLine
  _    <- IO.println(s"Hello, $name!")
} yield ()
```

---

## IOApp — Entry Point

โปรแกรม Cats Effect ต้อง Extend `IOApp` แทน `App` ปกติ

```scala
import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {
  def run: IO[Unit] = for {
    _    <- IO.println("Enter your name:")
    name <- IO.readLine
    _    <- IO.println(s"Hello, $name!")
  } yield ()
}
```

`IOApp` จะรัน `run` และจัดการ Runtime (Thread Pool, Signal Handling) ให้

---

## Error Handling ใน IO

```scala
import cats.effect.IO

// handleError — จัดการ Error แล้วให้ Default Value
val safe: IO[String] =
  IO(riskyOperation())
    .handleError(err => s"Error: ${err.getMessage}")

// attempt — แปลง IO[A] เป็น IO[Either[Throwable, A]]
val result: IO[Either[Throwable, String]] =
  IO(riskyOperation()).attempt

// ใช้ result
val program: IO[Unit] = result.flatMap {
  case Right(value) => IO.println(s"Success: $value")
  case Left(error)  => IO.println(s"Failed: ${error.getMessage}")
}
```

---

## Fiber — Concurrent Tasks

**Fiber** คือ "Thread เบา" ที่ Cats Effect จัดการเอง — สร้างได้หลายล้านตัวโดยไม่กิน RAM มาก

เปรียบเหมือน **งานที่ส่งให้ทำในเบื้องหลัง** — เราสั่งแล้วไม่ต้องรอ ค่อยกลับมาเอาผลทีหลัง

```scala
import cats.effect.{IO, IOApp}
import scala.concurrent.duration._

object FiberExample extends IOApp.Simple {
  def fetchData(id: Int): IO[String] =
    IO.sleep(1.second) *> IO.pure(s"Data-$id")

  def run: IO[Unit] = for {
    // Start สองงานพร้อมกัน (ไม่รอกัน)
    fiber1 <- fetchData(1).start
    fiber2 <- fetchData(2).start

    // รอผลจากทั้งคู่
    result1 <- fiber1.join
    result2 <- fiber2.join

    _ <- IO.println(s"Got: $result1, $result2")
  } yield ()
}
// รันแค่ ~1 วินาที แทนที่จะรัน 2 วินาที
```

### parMapN — รัน IO หลายตัวพร้อมกัน (วิธีที่สะดวกกว่า)

```scala
import cats.syntax.parallel._

val program: IO[Unit] = (
  fetchData(1),
  fetchData(2),
  fetchData(3)
).parMapN { (r1, r2, r3) =>
  println(s"Results: $r1, $r2, $r3")
}
```

---

## Resource — จัดการ Resource ที่ต้องปิด

ปัญหาคลาสสิก: เปิดไฟล์/Connection แล้วลืมปิด

```scala
// ❌ แบบนี้อันตราย — ถ้า use() throw Exception จะไม่ได้ close()
val conn = openConnection()
conn.use()
conn.close()  // อาจไม่ถูกเรียก!
```

`Resource` แก้ปัญหานี้ด้วยการ Guarantee ว่า finalizer จะรันเสมอ ไม่ว่าจะเกิด Error หรือไม่

```scala
import cats.effect.{IO, Resource}

// สร้าง Resource
def dbConnection(url: String): Resource[IO, Connection] =
  Resource.make(
    IO(openConnection(url))   // Acquire: เปิด Connection
  )(conn =>
    IO(conn.close())          // Release: ปิด Connection (รันเสมอ)
  )

// ใช้งาน
val program: IO[Unit] =
  dbConnection("jdbc:postgresql://localhost/mydb").use { conn =>
    IO(conn.query("SELECT * FROM users"))
      .flatMap(rows => IO.println(s"Got ${rows.size} rows"))
  }
// conn.close() จะถูกเรียกอัตโนมัติหลัง use block จบ
// ไม่ว่าจะ Success หรือ Exception
```

---

## ตัวอย่างรวม

```scala
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.parallel._
import scala.concurrent.duration._

object DataPipeline extends IOApp.Simple {

  // จำลองการดึงข้อมูลจาก Source ต่าง ๆ
  def fetchFromSource(source: String): IO[List[String]] =
    IO.sleep(500.millis) *>
    IO.pure(List(s"$source-record-1", s"$source-record-2"))

  // ดึงข้อมูลพร้อมกันจากหลาย Source
  def fetchAll: IO[List[String]] = (
    fetchFromSource("db"),
    fetchFromSource("api"),
    fetchFromSource("file")
  ).parMapN { (db, api, file) =>
    db ++ api ++ file
  }

  def run: IO[Unit] = for {
    _       <- IO.println("Starting pipeline...")
    records <- fetchAll
    _       <- IO.println(s"Fetched ${records.size} records")
    _       <- records.traverse_(r => IO.println(s"  - $r"))
    _       <- IO.println("Done!")
  } yield ()
}
```

---
## อ่านเพิ่มเติม
- [Cats Effect Docs](https://typelevel.org/cats-effect/)
- [Cats Effect Tutorial](https://typelevel.org/cats-effect/docs/tutorial)


## JVM คืออะไร?

ลองนึกถึง **ล่ามแปลภาษา** ที่นั่งอยู่ระหว่างโปรแกรมของเรากับเครื่องคอมพิวเตอร์

```
Source Code (.scala / .java / .kt)
          │
          ▼  (Compiler แปล)
      Bytecode (.class)
          │
          ▼  (JVM อ่านและรัน)
   คำสั่ง CPU จริง ๆ
```

**JVM (Java Virtual Machine)** คือ Runtime ที่รัน Bytecode แทนที่จะรัน Machine Code โดยตรง

ข้อดีของแนวคิดนี้:
- เขียนครั้งเดียว รันได้ทุก OS ที่มี JVM ("Write Once, Run Anywhere")
- ทุกภาษาที่ Compile เป็น Bytecode ได้เหมือนกัน → ใช้ Library ร่วมกันได้

---

## ทำไม Java, Scala, Kotlin ใช้ Library ร่วมกันได้?

เพราะทั้ง 3 ภาษา **Compile ไปที่เป้าหมายเดียวกัน** คือ JVM Bytecode

```
Java   ──┐
Scala  ──┼──► JVM Bytecode ──► JVM รัน
Kotlin ──┘
```

ในทางปฏิบัติ หมายความว่า:
- Spark เขียนด้วย Scala → ใช้ได้จาก Java และ Kotlin
- Cats Effect เขียนด้วย Scala → เรียกใช้ Library ของ Java ได้ตรง ๆ
- Library Java ที่มีมา 20 ปี → Scala เรียกใช้ได้เลยโดยไม่ต้อง Wrap

---

## Maven Repository คืออะไร?

เปรียบเหมือน **App Store สำหรับ Library** ของโลก JVM

| เปรียบเทียบ | JVM Ecosystem |
|-------------|--------------|
| npm (Node.js) | Maven Repository |
| package.json | `build.sbt` หรือ `pom.xml` |
| `npm install` | `sbt update` หรือ `mvn install` |
| node_modules/ | `~/.ivy2/` หรือ `~/.m2/` (Cache ในเครื่อง) |

**Maven Central** ([mvnrepository.com](https://mvnrepository.com)) คือคลัง Public หลักที่เก็บ Library เกือบทั้งหมดของ JVM

---

## วิธีระบุ Dependency ใน sbt (Scala)

Library ใน JVM ใช้ระบบตำแหน่งแบบ **3 ส่วน**:

```
GroupId : ArtifactId : Version
   │           │          │
   │           │          └── เวอร์ชันที่ต้องการ
   │           └─────────── ชื่อ Library
   └─────────────────────── ชื่อองค์กร/โปรเจกต์
```

ตัวอย่างใน `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // Apache Spark
  "org.apache.spark" %% "spark-core" % "3.5.0",
  "org.apache.spark" %% "spark-sql"  % "3.5.0",

  // Cats Effect
  "org.typelevel" %% "cats-effect" % "3.5.4",

  // Cats Core
  "org.typelevel" %% "cats-core" % "2.10.0"
)
```

> **`%%` vs `%`**
> - `%%` = sbt จะเติม Scala version ให้อัตโนมัติ (เช่น `_2.13` หรือ `_3`)
> - `%` = ระบุ ArtifactId แบบเต็มเอง

---

## Scala Version และ Binary Compatibility

Library ที่ Compile ด้วย Scala 2.13 **ใช้ไม่ได้กับ** Scala 3 โดยตรง เพราะ Bytecode แม้จะวิ่งบน JVM เหมือนกัน แต่ Encoding ของ Scala-specific feature ต่างกัน

```
spark-core_2.13-3.5.0.jar  ← ใช้กับ Scala 2.13
spark-core_3-3.5.0.jar     ← ใช้กับ Scala 3
```

`%%` ใน sbt เลือก Suffix ที่ถูกให้อัตโนมัติตาม `scalaVersion` ใน `build.sbt`

---

## โครงสร้างโปรเจกต์ Scala (sbt)

```
my-project/
├── build.sbt          ← กำหนด Dependency, Scala Version
├── project/
│   └── build.properties  ← กำหนด sbt version
└── src/
    ├── main/
    │   └── scala/     ← Source Code หลัก
    └── test/
        └── scala/     ← Test Code
```

ตัวอย่าง `build.sbt` เบื้องต้น:

```scala
ThisBuild / scalaVersion := "3.3.3"
ThisBuild / version      := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "my-spark-project",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "3.5.0"
    )
  )
```

---

## คำสั่ง sbt ที่ใช้บ่อย

| คำสั่ง | ความหมาย |
|--------|----------|
| `sbt compile` | Compile Source Code |
| `sbt run` | รัน Main Class |
| `sbt test` | รัน Test ทั้งหมด |
| `sbt update` | Download Dependency ที่ยังไม่มี |
| `sbt package` | สร้าง JAR file |
| `sbt "runMain com.example.Main"` | รัน Class ที่ระบุ |

- [sbt Reference Manual](https://www.scala-sbt.org/1.x/docs/)
- [Maven Repository](https://mvnrepository.com/)
