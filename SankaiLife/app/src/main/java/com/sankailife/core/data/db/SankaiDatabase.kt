package com.sankailife.core.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sankailife.core.data.db.dao.*
import com.sankailife.core.data.db.entities.*
import com.sankailife.core.garden.data.GardenCrateEntity
import com.sankailife.core.garden.data.GardenCropEntity
import com.sankailife.core.garden.data.GardenDao
import com.sankailife.core.garden.data.GardenInventoryEntity
import com.sankailife.core.garden.data.GardenMimoEntity
import com.sankailife.core.garden.data.GardenPlotEntity
import com.sankailife.core.garden.data.GardenStateEntity
import com.sankailife.core.garden.data.MemoChallengeEntity

@Database(
    entities = [UserEntity::class, MemoProfileEntity::class, MemoLineEntity::class,
                ObjectiveEntity::class, ArenaRewardEntity::class, ChestEntity::class,
                ChallengeEntity::class, StatsEntity::class, DayRecordEntity::class,
                GardenStateEntity::class, GardenPlotEntity::class, GardenCropEntity::class,
                MemoChallengeEntity::class,
                GardenCrateEntity::class, GardenInventoryEntity::class,
                GardenMimoEntity::class],
    version = 15,
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

        /**
         * Caisses de récolte et stock du dépôt.
         *
         * Purement additive : les cultures déjà récoltées avant cette version
         * ont été payées immédiatement, elles ne réapparaissent pas en caisse.
         * Le nouveau circuit ne s'applique donc qu'aux récoltes suivantes.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `garden_crate` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `seedId` TEXT NOT NULL,
                        `qualite` TEXT NOT NULL,
                        `creeALeMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `garden_inventory` (
                        `cle` TEXT PRIMARY KEY NOT NULL,
                        `seedId` TEXT NOT NULL,
                        `qualite` TEXT NOT NULL,
                        `quantite` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Les Mimos. Table neuve, aucun jardin existant n'est touché. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `garden_mimo` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `nom` TEXT NOT NULL,
                        `embaucheMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Le jardin passe d'une liste linéaire à un plan cartésien.
         *
         * C'est la seule migration de tout le projet qui réécrit des clés
         * primaires. Les seize parcelles existantes, indexées 0 à 15 et lues
         * comme quatre colonnes, sont recentrées autour de (20, 20) sur une
         * grille de 40 : l'ancien index `i` devient `(18 + i/4) * 40 + (18 + i%4)`.
         *
         * Les nouvelles clés valent au minimum 738, les anciennes au maximum
         * 15. Aucune collision n'est possible pendant la réécriture, ce qui
         * permet de faire la conversion en deux UPDATE au lieu d'une table
         * temporaire. `garden_crop.plotId` suit avec la même formule, sinon
         * les cultures en cours se retrouveraient orphelines.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garden_plot ADD COLUMN deblocage TEXT NOT NULL DEFAULT 'CACHEE'")
                db.execSQL("ALTER TABLE garden_plot ADD COLUMN terrain TEXT NOT NULL DEFAULT 'ORDINAIRE'")
                db.execSQL("ALTER TABLE garden_plot ADD COLUMN chantierFinMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE garden_plot ADD COLUMN humidite REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE garden_plot ADD COLUMN dernierCalculHumidite INTEGER NOT NULL DEFAULT 0")

                // Les parcelles déjà cultivables restent acquises : on ne
                // reprend pas au joueur ce qu'il avait ouvert.
                db.execSQL(
                    "UPDATE garden_plot SET deblocage = 'DEBLOQUEE' " +
                        "WHERE etat != 'LOCKED'"
                )

                // Recentrage. L'ordre importe : les cultures sont remappées
                // avant les parcelles, tant que les anciens identifiants sont
                // encore ceux de la table.
                db.execSQL(
                    "UPDATE garden_crop SET plotId = (18 + plotId / 4) * 40 + (18 + plotId % 4) " +
                        "WHERE plotId < 16"
                )
                db.execSQL(
                    "UPDATE garden_plot SET id = (18 + id / 4) * 40 + (18 + id % 4) " +
                        "WHERE id < 16"
                )
            }
        }

        /** Niveau de l'arrosoir. Tout le monde repart du niveau 1. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garden_state ADD COLUMN niveauArrosoir INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Langue déclarée d'un module mémo.
         *
         * Vide pour tout l'existant : aucun module déjà installé n'avait de
         * quoi la renseigner, et lui en inventer une ferait prononcer du
         * contenu dans une langue qui n'est pas la sienne.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memo_profile ADD COLUMN langue TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15
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
