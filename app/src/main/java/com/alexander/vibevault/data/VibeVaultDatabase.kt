package com.alexander.vibevault.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.alexander.vibevault.data.model.ConsumptionEntry
import androidx.room.Room
import androidx.room.Dao
import android.content.Context
import androidx.room.Insert
import androidx.room.Query
import com.alexander.vibevault.data.model.Converters

@Database(entities = [ConsumptionEntry::class], version = 1)
@TypeConverters(Converters::class)
abstract class VibeVaultDatabase : RoomDatabase() {
    abstract fun consumptionDao(): ConsumptionDao

    companion object {
        @Volatile
        private var INSTANCE: VibeVaultDatabase? = null

        fun getDatabase(context: Context): VibeVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VibeVaultDatabase::class.java,
                    "nictrack_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface ConsumptionDao {
    @Insert
    suspend fun insert(entry: ConsumptionEntry)

    @Query("SELECT * FROM consumption_entries ORDER BY timestamp DESC")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<ConsumptionEntry>>
}