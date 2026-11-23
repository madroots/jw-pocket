package app.jwpocket.core.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Where the person is in a video. Finished videos stay, for History and "Watched" marks. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val videoId: String,
    val videoJson: String,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoId: String,
    val videoJson: String,
    val addedAt: Long,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE videoId = :videoId")
    suspend fun get(videoId: String): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProgressEntity)

    @Query("DELETE FROM progress WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun exists(videoId: String): Boolean
}

@Database(entities = [ProgressEntity::class, FavoriteEntity::class], version = 1, exportSchema = false)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
    abstract fun favorites(): FavoriteDao

    companion object {
        fun create(context: Context): PocketDatabase =
            Room.databaseBuilder(context.applicationContext, PocketDatabase::class.java, "jw_pocket.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
