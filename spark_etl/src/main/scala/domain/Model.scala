package domain

import java.sql.Timestamp

// Bronze metadata
case class BronzeMetadata(
  source_file: String,
  ingested_at: Timestamp,
  job_id: String
)

// Source config for Bronze job
case class SourceConfig(
  path: String,              // "raw/runners.json"
  format: String,            // "json" | "excel"
  targetTable: String,       // "bronze_runners"
  sheetName: Option[String] = None
)
