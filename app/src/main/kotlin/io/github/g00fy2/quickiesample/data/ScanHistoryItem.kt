package io.github.g00fy2.quickiesample.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryItem(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val content: String,
  val type: String, // "WIFI", "URL", "TEXT"
  val title: String,
  val timestamp: Long = System.currentTimeMillis(),
  val wifiSsid: String? = null,
  val wifiPassword: String? = null,
  val wifiEncryption: String? = null
)
