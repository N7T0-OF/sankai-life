package com.sankailife.core.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sankailife.core.data.db.dao.*
import com.sankailife.core.data.db.entities.*
import com.sankailife.core.garden.data.GardenCropEntity
import com.sankailife.core.garden.data.GardenDao
import com.sankailife.core.garden.data.GardenPlotEntity
import com.sankailife.core.garden.data.GardenStateEntity
import com.sankailife.core.garden.data.MemoChallengeEntity

@Database(
    entities = [UserEntity::class, MemoProfileEntity::class, MemoLineEntity::class,
                ObjectiveEntity::class, ArenaRewardEntity::class, ChestEntity::class,
                ChallengeEntity::class, StatsEntity::class, DayRecordEntity::class,
                GardenStateEntity::class, GardenPlotEntity::class, GardenCropEntity::class,
                MemoChallengeEntity::class],
    version = 10,
    exportSchema = false
)
abstract class SankaiDatabase : RoomDatabase() {
    abstract fun gardenDao(): GardenDao
    abstract fun userDao(): UserDao
    abstract fun memoDao(): MemoDao
    abstract fun objectiveDao(): ObjectiveDao
    abstract fun arenaRewardDao(): ArenaRewardDao
    abstract fun dayRecordDao(): DayRecordDao
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `arena_reward` (
                        `arenaId` INTEGER PRIMARY KEY NOT NULL,
                        `claimedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // bestStreak démarre à la série en cours plutôt qu'à 0 :
                // remettre le record d'un joueur existant à zéro serait vécu
                // comme une perte, alors qu'il a bien réalisé cette série.
                db.execSQL("ALTER TABLE user ADD COLUMN bestStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE user SET bestStreak = streakDays")
                db.execSQL("ALTER TABLE user ADD COLUMN streakShields INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `day_record` (
                        `date` TEXT PRIMARY KEY NOT NULL,
                        `status` TEXT NOT NULL,
                        `note` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Mode Jardin. Migration purement additive : trois tables neuves,
         * aucune table existante touchée. L'outil de productivité continue de
         * fonctionner à l'identique pour qui n'ouvre jamais le jardin.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `garden_state` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `eau` INTEGER NOT NULL,
                        `compost` INTEGER NOT NULL,
                        `cristaux` INTEGER NOT NULL,
                        `gouttes` INTEGER NOT NULL,
                        `eauGagneeAujourdhui` INTEGER NOT NULL,
                        `jourPlafond` TEXT NOT NULL,
                        `revisionsDepuisOuverture` INTEGER NOT NULL,
                        `derniereHeureMurale` INTEGER NOT NULL,
                        `dernierElapsedRealtime` INTEGER NOT NULL,
                        `zoneActive` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `garden_plot` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `etat` TEXT NOT NULL,
                        `solId` TEXT NOT NULL,
                        `areneRequise` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `garden_crop` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `plotId` INTEGER NOT NULL,
                        `seedId` TEXT NOT NULL,
                        `plantedAtMillis` INTEGER NOT NULL,
                        `minutesCumulees` INTEGER NOT NULL,
                        `dernierArrosageMillis` INTEGER NOT NULL,
                        `arrosages` INTEGER NOT NULL,
                        `revisionsPendantCulture` INTEGER NOT NULL,
                        `recoltee` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Défi souvenir : trace des notifications mémo envoyées. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memo_challenge` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` INTEGER NOT NULL,
                        `lineId` INTEGER NOT NULL,
                        `texte` TEXT NOT NULL,
                        `nomModule` TEXT NOT NULL,
                        `envoyeALeMillis` INTEGER NOT NULL,
                        `reclame` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Verrou par date du coffre quotidien. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN lastDailyChestDay TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
        )

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
