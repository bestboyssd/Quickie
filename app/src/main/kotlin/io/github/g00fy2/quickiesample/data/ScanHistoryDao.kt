package io.github.g00fy2.quickiesample.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
  @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
  fun getAll(): Flow<List<ScanHistoryItem>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: ScanHistoryItem): Long

  @Delete
  suspend fun delete(item: ScanHistoryItem): Int

  @Query("DELETE FROM scan_history")
  suspend fun clearAll(): Int
}
