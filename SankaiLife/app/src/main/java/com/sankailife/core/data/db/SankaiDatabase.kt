package com.sankailife.core.data.db

import android.content.Context
import androidx.room.*
import com.sankailife.core.data.db.dao.*
import com.sankailife.core.data.db.entities.*

@Database(
    entities = [UserEntity::class, MemoProfileEntity::class, MemoLineEntity::class,
                ObjectiveEntity::class, ChestEntity::class, ChallengeEntity::class,
                StatsEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SankaiDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun memoDao(): MemoDao
    abstract fun objectiveDao(): ObjectiveDao
    abstract fun chestDao(): ChestDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile private var INSTANCE: SankaiDatabase? = null

        fun getDatabase(context: Context): SankaiDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, SankaiDatabase::class.java, "sankai_db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
