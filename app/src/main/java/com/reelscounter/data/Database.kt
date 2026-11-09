package com.reelscounter.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One row per reel/short that became visible. */
@Entity(tableName = "reel_events", indices = [Index("day")])
data class ReelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val day: String,
    val app: String,
)

/** Seconds spent inside a Reels/Shorts feed, per day and app. */
@Entity(tableName = "watch_time", primaryKeys = ["day", "app"])
data class WatchTime(
    val day: String,
    val app: String,
    val seconds: Long,
)

data class AppCount(val app: String, val count: Int)
data class DayAppCount(val day: String, val app: String, val count: Int)

@Dao
abstract class ReelDao {
    @Insert
    abstract suspend fun insertEvent(event: ReelEvent)

    @Query("SELECT app, COUNT(*) AS count FROM reel_events WHERE day = :day GROUP BY app")
    abstract fun observeCounts(day: String): Flow<List<AppCount>>

    @Query("SELECT * FROM watch_time WHERE day = :day")
    abstract fun observeWatchTime(day: String): Flow<List<WatchTime>>

    @Query("SELECT day, app, COUNT(*) AS count FROM reel_events WHERE day >= :fromDay GROUP BY day, app")
    abstract fun observeCountsSince(fromDay: String): Flow<List<DayAppCount>>

    @Query("SELECT * FROM watch_time WHERE day >= :fromDay")
    abstract fun observeWatchTimeSince(fromDay: String): Flow<List<WatchTime>>

    @Query("INSERT OR IGNORE INTO watch_time(day, app, seconds) VALUES(:day, :app, 0)")
    abstract suspend fun ensureWatchRow(day: String, app: String)

    @Query("UPDATE watch_time SET seconds = seconds + :seconds WHERE day = :day AND app = :app")
    abstract suspend fun incrementWatch(day: String, app: String, seconds: Long)

    @Transaction
    open suspend fun addSeconds(day: String, app: String, seconds: Long) {
        ensureWatchRow(day, app)
        incrementWatch(day, app, seconds)
    }

    @Query("DELETE FROM reel_events WHERE day = :day")
    abstract suspend fun deleteEvents(day: String)

    @Query("DELETE FROM watch_time WHERE day = :day")
    abstract suspend fun deleteWatchTime(day: String)

    @Transaction
    open suspend fun clearDay(day: String) {
        deleteEvents(day)
        deleteWatchTime(day)
    }
}

@Database(entities = [ReelEvent::class, WatchTime::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reels.db").build()
    }
}
