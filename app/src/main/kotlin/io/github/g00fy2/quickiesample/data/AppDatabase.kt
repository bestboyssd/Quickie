package io.github.g00fy2.quickiesample.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanHistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun scanHistoryDao(): ScanHistoryDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "quickie_history_db"
        ).build()
        INSTANCE = instance
        instance
      }
    }
  }
}
