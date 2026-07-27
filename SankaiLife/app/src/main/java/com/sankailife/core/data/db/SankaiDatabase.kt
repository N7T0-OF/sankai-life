package com.sankailife.core.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sankailife.core.data.db.dao.*
import com.sankailife.core.data.db.entities.*

@Database(
    entities = [UserEntity::class, MemoProfileEntity::class, MemoLineEntity::class,
                ObjectiveEntity::class, ChestEntity::class, ChallengeEntity::class,
                StatsEntity::class],
    version = 5,
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

        /**
         * Migrations explicites, jamais destructives.
         *
         * Toute la progression du joueur (niveau, mémos, statistiques) vit
         * uniquement ici : il n'existe aucun serveur pour la reconstituer.
         * Une migration manquante effacerait définitivement les données, donc
         * `fallbackToDestructiveMigration` est volontairement absent — une
         * mise à jour ratée doit planter bruyamment plutôt que tout effacer.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memo_profile ADD COLUMN lastNotifiedAtMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `objective` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `isDone` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN randomMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN randomStartHour INTEGER NOT NULL DEFAULT 9")
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN randomStartMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN randomEndHour INTEGER NOT NULL DEFAULT 21")
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN randomEndMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN activeDays TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'")
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN nextTriggerAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // État de révision des flash cards. Valeurs par défaut choisies
                // pour que toutes les lignes déjà saisies soient immédiatement
                // révisables, sans traitement de rattrapage.
                db.execSQL("ALTER TABLE memo_line ADD COLUMN box INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memo_line ADD COLUMN nextReviewAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memo_line ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memo_line ADD COLUMN successCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun getDatabase(context: Context): SankaiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SankaiDatabase::class.java,
                    "sankai_db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
