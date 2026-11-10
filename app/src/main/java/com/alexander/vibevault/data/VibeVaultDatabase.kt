package com.alexander.vibevault.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alexander.vibevault.data.model.ConsumptionEntry
import com.alexander.vibevault.data.model.Converters
import kotlinx.coroutines.flow.Flow

@Database(entities = [ConsumptionEntry::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class VibeVaultDatabase : RoomDatabase() {
    abstract fun consumptionDao(): ConsumptionDao

    companion object {
        @Volatile
        private var INSTANCE: VibeVaultDatabase? = null

        /** v2: Nutzungsdauer pro Pouch. Bestehende Einträge behalten "unbekannt" (NULL). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE consumption_entries ADD COLUMN durationMinutes INTEGER")
            }
        }

        /** v3: Auslöser, Mundseite und Gefühl danach (alle optional). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE consumption_entries ADD COLUMN `trigger` TEXT")
                db.execSQL("ALTER TABLE consumption_entries ADD COLUMN side TEXT")
                db.execSQL("ALTER TABLE consumption_entries ADD COLUMN feeling TEXT")
            }
        }

        fun getDatabase(context: Context): VibeVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VibeVaultDatabase::class.java,
                    "nictrack_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

@Dao
interface ConsumptionDao {
    @Insert
    suspend fun insert(entry: ConsumptionEntry): Long

    @Delete
    suspend fun delete(entry: ConsumptionEntry)

    @Query("UPDATE consumption_entries SET durationMinutes = :minutes WHERE id = :id")
    suspend fun setDuration(id: Int, minutes: Int)

    @Query("UPDATE consumption_entries SET feeling = :feeling WHERE id = :id")
    suspend fun setFeeling(id: Int, feeling: String?)

    @Query("SELECT * FROM consumption_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ConsumptionEntry>>
}
