package io.github.g00fy2.quickiesample.data

import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanHistoryDao) {
  val allHistory: Flow<List<ScanHistoryItem>> = dao.getAll()

  suspend fun insert(item: ScanHistoryItem): Long = dao.insert(item)

  suspend fun delete(item: ScanHistoryItem): Int = dao.delete(item)

  suspend fun clearAll(): Int = dao.clearAll()
}
