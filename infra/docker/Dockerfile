# Stage 1: Build (ใช้ Cache ให้เป็นประโยชน์)
FROM sbtscala/scala-sbt:eclipse-temurin-alpine-21.0.8_9_1.11.7_2.13.18 AS builder
WORKDIR /app

ENV SBT_OPTS="-Xmx4G -XX:+UseG1GC"
# แยก Copy build.sbt เพื่อทำ Layer Cache สำหรับ dependencies
COPY spark_etl/build.sbt .
COPY spark_etl/project ./project
RUN sbt update

COPY spark_etl/src ./src
RUN sbt clean assembly

# Stage 2: Spark Image (Production Base)
FROM spark:4.0.1-scala2.13-java21-ubuntu

USER root

# ติดตั้งเฉพาะที่จำเป็นจริงๆ
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# ดาวน์โหลด AWS SDK Bundle (แนะนำเวอร์ชันที่เข้ากับ Hadoop 3.4.1)
# Spark 4.0.1 ใช้ Hadoop 3.4.1 ซึ่งแนะนำ AWS SDK V2
ARG AWS_SDK_V2_VERSION=2.26.25
RUN curl -sL -o /opt/spark/jars/bundle-${AWS_SDK_V2_VERSION}.jar \
    https://repo1.maven.org/maven2/software/amazon/awssdk/bundle/${AWS_SDK_V2_VERSION}/bundle-${AWS_SDK_V2_VERSION}.jar

# เตรียมโฟลเดอร์แอป
RUN mkdir -p /opt/spark/etl && chmod -R 777 /opt/spark/etl

# Copy JAR จาก Builder Stage
# ใช้คำสั่งนี้เพื่อให้มั่นใจว่าเราหยิบไฟล์ Fat Jar (Assembly) มาถูกตัว
COPY --from=builder /app/target/scala-2.13/*.jar /opt/spark/etl/app.jar

# ไม่ต้อง Copy run_etl.sh แล้ว เพราะเราเรียกผ่าน Spark Operator ตรงๆ
# และไม่ต้องตั้ง WORKDIR ใหม่ เพราะ Spark Image มีมาตรฐานของมันอยู่แล้ว

# ตั้งค่า ENV พื้นฐาน
ENV SPARK_APPLICATION_JAR=local:///opt/spark/etl/app.jar

# กลับมาใช้ user spark เพื่อความปลอดภัย (Security Context)
USER spark

# ใช้ Entrypoint เดิมของ Spark (เพื่อให้ Spark Operator สั่งงานได้ถูกต้อง)
ENTRYPOINT ["/opt/entrypoint.sh"]
