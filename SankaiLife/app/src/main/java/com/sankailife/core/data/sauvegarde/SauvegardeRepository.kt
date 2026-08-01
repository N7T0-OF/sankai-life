package com.sankailife.core.data.sauvegarde

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sankailife.BuildConfig
import com.sankailife.core.data.archive.BoundedZipReader
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.*
import com.sankailife.core.garden.data.*
import com.sankailife.core.notifications.CoffreAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sauvegarde et restauration du profil.
 *
 * Le format est une archive ZIP contenant du JSON, pas une copie du fichier de
 * base de données. Copier le fichier serait plus court mais bien plus fragile :
 * SQLite écrit dans un journal séparé, une copie prise au mauvais moment est
 * incohérente, et le fichier ne se relit qu'avec exactement le même schéma.
 * Du JSON se relit même après une migration.
 *
 * **Rien ne sort du profil.** Ni cache, ni publicités, ni jetons, ni fichiers
 * temporaires : uniquement ce que la personne a construit.
 *
 * Tout fonctionne hors ligne, sans compte, sans serveur. Le sélecteur de
 * fichiers Android choisit la destination — l'application n'écrit jamais où
 * elle veut.
 */
class SauvegardeRepository(
    private val contexte: Context,
    private val db: SankaiDatabase
) {

    // --- Export ------------------------------------------------------------

    /**
     * Écrit une sauvegarde à l'emplacement choisi.
     * @return le nombre d'octets écrits.
     */
    suspend fun exporter(destination: Uri): Long = withContext(Dispatchers.IO) {
        val sections = db.withTransaction { collecter() }

        // Le contenu est assemblé en mémoire d'abord : l'empreinte doit porter
        // sur ce qui sera réellement écrit, et on ne peut pas la calculer sur
        // un flux déjà parti.
        val fichiers = sections.mapValues { (_, json) ->
            json.toString().toByteArray(Charsets.UTF_8)
        }
        val empreinte = SauvegardeEngine.empreinte(fichiers.toSortedMap().values)

        val manifeste = JSONObject().apply {
            put("versionFormat", SauvegardeEngine.VERSION_FORMAT)
            put("appVersionCode", BuildConfig.VERSION_CODE)
            put("creeLe", java.time.Instant.now().toString())
            put("sections", JSONArray(fichiers.keys.toList()))
            put("empreinte", empreinte)
        }

        var octets = 0L
        contexte.contentResolver.openOutputStream(destination)?.use { sortie ->
            ZipOutputStream(sortie).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                val entete = manifeste.toString(2).toByteArray(Charsets.UTF_8)
                zip.write(entete)
                zip.closeEntry()
                octets += entete.size

                fichiers.forEach { (nom, contenu) ->
                    zip.putNextEntry(ZipEntry("$nom.json"))
                    zip.write(contenu)
                    zip.closeEntry()
                    octets += contenu.size
                }
            }
        } ?: error("Impossible d'écrire à cet emplacement.")

        octets
    }

    private suspend fun collecter(): Map<String, JSONObject> {
        val profil = JSONObject().apply {
            db.userDao().getUserOnce()?.let { put("user", it.enJson()) }
            put("dayRecords", db.dayRecordDao().getDepuisOnce("").enTableauJson { it.enJson() })
            put("stats", db.statsDao().getAllOnce().enTableauJson { it.enJson() })
            put(
                "arenaRewards",
                db.arenaRewardDao().getAllRewardsOnce().enTableauJson { it.enJson() }
            )
            put("objectives", db.objectiveDao().getAllOnce().enTableauJson { it.enJson() })
        }

        // Les mémos restent une section séparée : lors d'une restauration ils
        // sont copiés avec de nouveaux identifiants, jamais remplacés.
        val profilsJson = JSONArray()
        db.memoDao().getAllProfilesOnce().forEach { p ->
            profilsJson.put(
                p.enJson().apply {
                    put("lignes", db.memoDao().getLinesOnce(p.id).enTableauJson { it.enJson() })
                }
            )
        }
        val memos = JSONObject().put("profils", profilsJson)

        val jardin = JSONObject().apply {
            db.gardenDao().etat()?.let { put("etat", it.enJson()) }
            put("parcelles", db.gardenDao().parcelles().enTableauJson { it.enJson() })
            put("cultures", db.gardenDao().toutesCultures().enTableauJson { it.enJson() })
            put("caisses", db.gardenDao().caisses().enTableauJson { it.enJson() })
            put("inventaire", db.gardenDao().inventaire().enTableauJson { it.enJson() })
            put("mimos", db.gardenDao().mimos().enTableauJson { it.enJson() })
            put(
                "defisSouvenir",
                db.gardenDao().defisSouvenir().enTableauJson { it.enJson() }
            )
        }

        val coffres = JSONObject().apply {
            put("coffres", db.chestDao().getAllOnce().enTableauJson { it.enJson() })
            put("defis", db.challengeDao().getAllOnce().enTableauJson { it.enJson() })
        }

        // REGLAGES n'est volontairement pas présent : DataStore ne fait pas
        // encore partie du format, donc le manifeste ne doit pas le promettre.
        return linkedMapOf(
            SauvegardeEngine.Section.PROFIL.cle to profil,
            SauvegardeEngine.Section.MEMOS.cle to memos,
            SauvegardeEngine.Section.JARDIN.cle to jardin,
            SauvegardeEngine.Section.COFFRES.cle to coffres
        )
    }

    // --- Import ------------------------------------------------------------

    /** Ce qu'on a lu dans un fichier, avant d'y toucher. */
    data class Apercu(
        val verdict: SauvegardeEngine.Verdict,
        val sections: List<SauvegardeEngine.Section>,
        val nombreCartes: Int
    )

    /**
     * Lit et contrôle un fichier sans rien modifier.
     *
     * Étape obligatoire : on n'écrase jamais une base active sur la foi d'un
     * nom de fichier.
     */
    suspend fun inspecter(source: Uri): Apercu = withContext(Dispatchers.IO) {
        analyser(lireArchive(source))
    }

    /** Analyse exactement les octets qui seront ensuite restaurés. */
    private fun analyser(contenu: Map<String, ByteArray>): Apercu {
        val manifesteBrut = contenu["manifest.json"]

        val manifeste = manifesteBrut?.let {
            runCatching {
                val o = JSONObject(String(it, Charsets.UTF_8))
                SauvegardeEngine.Manifeste(
                    versionFormat = o.optInt("versionFormat", 0),
                    appVersionCode = o.optInt("appVersionCode", 0),
                    creeLe = o.optString("creeLe"),
                    sections = (0 until o.optJSONArray("sections")!!.length())
                        .map { i -> o.getJSONArray("sections").getString(i) },
                    empreinte = o.optString("empreinte")
                )
            }.getOrNull()
        }

        val empreinte = SauvegardeEngine.empreinte(
            contenu.filterKeys { it != "manifest.json" }.toSortedMap().values
        )

        val verdictInitial = SauvegardeEngine.verifier(
            manifeste,
            empreinte,
            BuildConfig.VERSION_CODE
        )
        val sectionsManquantes = manifeste?.sections.orEmpty()
            .mapNotNull { cle ->
                SauvegardeEngine.Section.entries.firstOrNull {
                    it.priseEnCharge && it.cle == cle
                }
            }
            .filter { "${it.cle}.json" !in contenu }
        val verdict = if (
            verdictInitial is SauvegardeEngine.Verdict.Utilisable &&
            sectionsManquantes.isNotEmpty()
        ) {
            SauvegardeEngine.Verdict.Refuse(
                "Sauvegarde incomplète : il manque " +
                    sectionsManquantes.joinToString { "${it.cle}.json" } + "."
            )
        } else {
            verdictInitial
        }
        val sections = manifeste?.sections.orEmpty()
            .mapNotNull { cle ->
                SauvegardeEngine.Section.entries.firstOrNull {
                    it.priseEnCharge && it.cle == cle && "${it.cle}.json" in contenu
                }
            }

        val cartes = contenu["memos.json"]?.let { octets ->
            runCatching {
                val profils = JSONObject(String(octets, Charsets.UTF_8)).getJSONArray("profils")
                (0 until profils.length()).sumOf { i ->
                    profils.getJSONObject(i).getJSONArray("lignes").length()
                }
            }.getOrDefault(0)
        } ?: 0

        return Apercu(verdict, sections, cartes)
    }

    /**
     * Restaure les sections demandées.
     *
     * Une sauvegarde de sécurité est écrite avant toute modification, et toute
     * la restauration tient dans une transaction : une erreur au milieu laisse
     * la base exactement dans l'état d'avant. Sans ces deux protections, un
     * fichier abîmé à moitié lu détruirait le profil sans recours.
     *
     * PROFIL, JARDIN et COFFRES sont des instantanés : lorsqu'un tableau est
     * présent, il remplace la table correspondante. MEMOS suit une règle plus
     * conservatrice : chaque profil devient une copie inactive, avec de
     * nouveaux identifiants et aucune alarme héritée.
     *
     * @return les sections effectivement restaurées.
     */
    suspend fun restaurer(
        source: Uri,
        demandees: Set<SauvegardeEngine.Section>,
        sauvegardeDeSecurite: Uri
    ): List<SauvegardeEngine.Section> = withContext(Dispatchers.IO) {
        val contenu = lireArchive(source)
        val apercu = analyser(contenu)
        val verdict = apercu.verdict
        if (verdict !is SauvegardeEngine.Verdict.Utilisable) {
            error((verdict as SauvegardeEngine.Verdict.Refuse).raison)
        }

        // Obligatoire et non avalée : si l'export échoue, aucune ligne de la
        // base n'est modifiée.
        exporter(sauvegardeDeSecurite)

        val aFaire = SauvegardeEngine.sectionsARestaurer(
            verdict.manifeste.sections, demandees
        )

        db.withTransaction {
            var correspondanceMemos = CorrespondanceMemos()
            aFaire.forEach { section ->
                val octets = contenu["${section.cle}.json"] ?: return@forEach
                when (section) {
                    SauvegardeEngine.Section.PROFIL -> restaurerProfil(
                        JSONObject(String(octets, Charsets.UTF_8))
                    )
                    SauvegardeEngine.Section.MEMOS -> {
                        correspondanceMemos = restaurerMemos(
                            JSONObject(String(octets, Charsets.UTF_8))
                        )
                    }
                    SauvegardeEngine.Section.JARDIN -> restaurerJardin(
                        JSONObject(String(octets, Charsets.UTF_8)),
                        correspondanceMemos
                    )
                    SauvegardeEngine.Section.COFFRES -> restaurerCoffres(
                        JSONObject(String(octets, Charsets.UTF_8))
                    )
                    SauvegardeEngine.Section.REGLAGES -> Unit
                }
            }
        }
        if (SauvegardeEngine.Section.COFFRES in aFaire) {
            CoffreAlarmReceiver.replanifierTous(contexte)
        }
        aFaire
    }

    private suspend fun restaurerProfil(o: JSONObject) {
        val utilisateurJson = when {
            o.has("user") -> o.objetExige("user")
            // Compatibilité avec le premier format, où User était à la racine.
            o.has("pseudo") || o.has("level") -> o
            else -> null
        }
        utilisateurJson?.let { json ->
            val actuel = db.userDao().getUserOnce() ?: UserEntity()
            db.userDao().upsert(json.versUser(actuel).copy(id = 1L))
        }

        o.tableauOptionnel("dayRecords")?.let { tableau ->
            val records = tableau.mapperObjets("dayRecords") { json, _ ->
                json.versDayRecord()
            }
            db.dayRecordDao().clearAll()
            records.forEach { db.dayRecordDao().upsert(it) }
        }

        o.tableauOptionnel("stats")?.let { tableau ->
            val stats = tableau.mapperObjets("stats") { json, _ -> json.versStats() }
            db.statsDao().clearAll()
            stats.forEach { db.statsDao().upsert(it) }
        }

        o.tableauOptionnel("arenaRewards")?.let { tableau ->
            val recompenses = tableau.mapperObjets("arenaRewards") { json, _ ->
                json.versArenaReward()
            }
            db.arenaRewardDao().toutEffacer()
            recompenses.forEach { db.arenaRewardDao().marquerReclamee(it) }
        }

        o.tableauOptionnel("objectives")?.let { tableau ->
            val objectifs = tableau.mapperObjets("objectives") { json, _ ->
                json.versObjective()
            }
            db.objectiveDao().clearAll()
            objectifs.forEach { db.objectiveDao().upsert(it) }
        }
    }

    /**
     * Restaure les mémos en **ajoutant**, jamais en écrasant.
     *
     * Un module portant le même nom qu'un module existant est importé comme
     * copie inactive. Remplacer par erreur détruit un travail ; ajouter par
     * erreur ne fait que créer un doublon, qui se supprime en deux gestes.
     */
    private suspend fun restaurerMemos(o: JSONObject): CorrespondanceMemos {
        val nomsUtilises = db.memoDao().getAllProfilesOnce()
            .mapTo(mutableSetOf()) { it.name }
        val profils = o.tableauOptionnel("profils") ?: return CorrespondanceMemos()
        val profilsRemappes = mutableMapOf<Long, Long>()
        val lignesRemappees = mutableMapOf<Long, Long>()

        for (i in 0 until profils.length()) {
            val p = profils.optJSONObject(i)
                ?: error("Sauvegarde invalide : profils[$i] n'est pas un objet.")
            val sauvegarde = p.versMemoProfile()
            val nom = SauvegardeEngine.nomMemoDisponible(sauvegarde.name, nomsUtilises)
            val nouvelId = db.memoDao().upsertProfile(
                sauvegarde.copy(
                    id = 0L,
                    name = nom,
                    isActive = false,
                    sentLineHistory = "",
                    nextTriggerAtMillis = 0L
                )
            )
            nomsUtilises += nom
            if (sauvegarde.id > 0L) profilsRemappes[sauvegarde.id] = nouvelId

            val lignes = p.tableauOptionnel("lignes") ?: JSONArray()
            for (j in 0 until lignes.length()) {
                val l = lignes.optJSONObject(j)
                    ?: error("Sauvegarde invalide : lignes[$j] n'est pas un objet.")
                val ligneSauvegardee = l.versMemoLine(
                    MemoLineEntity(orderIndex = j)
                )
                val nouvelIdLigne = db.memoDao().insertLine(
                    ligneSauvegardee.copy(id = 0L, profileId = nouvelId)
                )
                if (ligneSauvegardee.id > 0L) {
                    lignesRemappees[ligneSauvegardee.id] = nouvelIdLigne
                }
            }

            val historiqueRemappe = sauvegarde.sentLineHistory
                .split(',')
                .mapNotNull { it.toLongOrNull() }
                .mapNotNull(lignesRemappees::get)
                .joinToString(",")
            db.memoDao().updateHistory(nouvelId, historiqueRemappe)
        }

        return CorrespondanceMemos(profilsRemappes, lignesRemappees)
    }

    private suspend fun restaurerJardin(
        o: JSONObject,
        correspondanceMemos: CorrespondanceMemos
    ) {
        val etatJson = when {
            o.has("etat") -> o.objetExige("etat")
            // Compatibilité avec le premier format, où l'état était à la racine.
            o.has("eau") || o.has("compost") -> o
            else -> null
        }
        etatJson?.let { json ->
            val actuel = db.gardenDao().etat() ?: GardenStateEntity()
            db.gardenDao().sauverEtat(json.versGardenState(actuel).copy(id = 1L))
        }

        o.tableauOptionnel("parcelles")?.let { tableau ->
            val existantes = db.gardenDao().parcelles().associateBy { it.id }
            val parcelles = tableau.mapperObjets("parcelles") { json, _ ->
                val id = json.optInt("id", -1)
                if (id < 0) error("Sauvegarde invalide : identifiant de parcelle absent.")
                json.versGardenPlot(existantes[id] ?: GardenPlotEntity(id = id))
            }
            db.gardenDao().effacerParcelles()
            if (parcelles.isNotEmpty()) db.gardenDao().sauverParcelles(parcelles)
        }

        o.tableauOptionnel("cultures")?.let { tableau ->
            val cultures = tableau.mapperObjets("cultures") { json, _ ->
                json.versGardenCrop()
            }
            db.gardenDao().effacerCultures()
            if (cultures.isNotEmpty()) db.gardenDao().sauverCultures(cultures)
        }

        o.tableauOptionnel("caisses")?.let { tableau ->
            val caisses = tableau.mapperObjets("caisses") { json, _ ->
                json.versGardenCrate()
            }
            db.gardenDao().effacerCaisses()
            caisses.forEach { db.gardenDao().poserCaisse(it) }
        }

        o.tableauOptionnel("inventaire")?.let { tableau ->
            val inventaire = tableau.mapperObjets("inventaire") { json, _ ->
                json.versGardenInventory()
            }
            db.gardenDao().effacerInventaire()
            inventaire.forEach { db.gardenDao().sauverInventaire(it) }
        }

        o.tableauOptionnel("mimos")?.let { tableau ->
            val mimos = tableau.mapperObjets("mimos") { json, _ -> json.versGardenMimo() }
            db.gardenDao().effacerMimos()
            mimos.forEach { db.gardenDao().embaucher(it) }
        }

        o.tableauOptionnel("defisSouvenir")?.let { tableau ->
            val defis = tableau.mapperObjets("defisSouvenir") { json, _ ->
                val sauvegarde = json.versMemoChallenge()
                sauvegarde.copy(
                    profileId = correspondanceMemos.profils[sauvegarde.profileId]
                        ?: 0L,
                    lineId = correspondanceMemos.lignes[sauvegarde.lineId]
                        ?: 0L
                )
            }
            db.gardenDao().effacerDefisSouvenir()
            defis.forEach { db.gardenDao().enregistrerNotification(it) }
        }
    }

    private suspend fun restaurerCoffres(o: JSONObject) {
        o.tableauOptionnel("coffres")?.let { tableau ->
            val coffres = tableau.mapperObjets("coffres") { json, _ -> json.versChest() }
            db.chestDao().clearAll()
            coffres.forEach { db.chestDao().insert(it) }
        }

        o.tableauOptionnel("defis")?.let { tableau ->
            val defis = tableau.mapperObjets("defis") { json, _ -> json.versChallenge() }
            db.challengeDao().clearAll()
            defis.forEach { db.challengeDao().upsert(it) }
        }
    }

    private data class CorrespondanceMemos(
        val profils: Map<Long, Long> = emptyMap(),
        val lignes: Map<Long, Long> = emptyMap()
    )

    // --- JSON explicite : chaque champ persistant apparaît ici ------------

    private fun <T> Iterable<T>.enTableauJson(conversion: (T) -> JSONObject): JSONArray =
        JSONArray().also { tableau -> forEach { tableau.put(conversion(it)) } }

    private fun JSONObject.objetExige(nom: String): JSONObject =
        optJSONObject(nom)
            ?: error("Sauvegarde invalide : '$nom' doit être un objet.")

    private fun JSONObject.tableauOptionnel(nom: String): JSONArray? {
        if (!has(nom)) return null
        return optJSONArray(nom)
            ?: error("Sauvegarde invalide : '$nom' doit être un tableau.")
    }

    private fun <T> JSONArray.mapperObjets(
        nom: String,
        conversion: (JSONObject, Int) -> T
    ): List<T> = (0 until length()).map { index ->
        val objet = optJSONObject(index)
            ?: error("Sauvegarde invalide : $nom[$index] doit être un objet.")
        conversion(objet, index)
    }

    private fun UserEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("pseudo", pseudo)
        put("level", level)
        put("xp", xp)
        put("xpNext", xpNext)
        put("coins", coins)
        put("gems", gems)
        put("streakDays", streakDays)
        put("lastLoginDate", lastLoginDate)
        put("totalFocusMinutes", totalFocusMinutes)
        put("totalAdsWatched", totalAdsWatched)
        put("totalChestsOpened", totalChestsOpened)
        put("equippedThemeId", equippedThemeId)
        put("unlockedThemeIds", unlockedThemeIds)
        put("moduleSlots", moduleSlots)
        put("focusSlots", focusSlots)
        put("memoProfileSlots", memoProfileSlots)
        put("adCountToday", adCountToday)
        put("lastAdDate", lastAdDate)
        put("totalCoinsEarned", totalCoinsEarned)
        put("totalCoinsSpent", totalCoinsSpent)
        put("bestStreak", bestStreak)
        put("streakShields", streakShields)
        put("lastDailyChestDay", lastDailyChestDay)
    }

    private fun DayRecordEntity.enJson() = JSONObject().apply {
        put("date", date)
        put("status", status)
        put("note", note)
    }

    private fun StatsEntity.enJson() = JSONObject().apply {
        put("date", date)
        put("xpGained", xpGained)
        put("coinsGained", coinsGained)
        put("coinsSpent", coinsSpent)
        put("focusSessions", focusSessions)
        put("focusMinutes", focusMinutes)
        put("chestsOpened", chestsOpened)
        put("adsWatched", adsWatched)
        put("memoLinesReceived", memoLinesReceived)
    }

    private fun ArenaRewardEntity.enJson() = JSONObject().apply {
        put("arenaId", arenaId)
        put("claimedAt", claimedAt)
    }

    private fun ObjectiveEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("isDone", isDone)
        put("createdAt", createdAt)
        put("completedAt", completedAt)
    }

    private fun MemoProfileEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("frequencyPerDay", frequencyPerDay)
        put("scheduledHour", scheduledHour)
        put("scheduledMinute", scheduledMinute)
        put("isActive", isActive)
        put("sentLineHistory", sentLineHistory)
        put("lastNotifiedAtMillis", lastNotifiedAtMillis)
        put("randomMode", randomMode)
        put("randomStartHour", randomStartHour)
        put("randomStartMinute", randomStartMinute)
        put("randomEndHour", randomEndHour)
        put("randomEndMinute", randomEndMinute)
        put("activeDays", activeDays)
        put("nextTriggerAtMillis", nextTriggerAtMillis)
    }

    private fun MemoLineEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("profileId", profileId)
        put("text", text)
        put("orderIndex", orderIndex)
        put("box", box)
        put("nextReviewAtMillis", nextReviewAtMillis)
        put("reviewCount", reviewCount)
        put("successCount", successCount)
    }

    private fun GardenStateEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("eau", eau)
        put("compost", compost)
        put("cristaux", cristaux)
        put("gouttes", gouttes)
        put("eauGagneeAujourdhui", eauGagneeAujourdhui)
        put("jourPlafond", jourPlafond)
        put("revisionsDepuisOuverture", revisionsDepuisOuverture)
        put("derniereHeureMurale", derniereHeureMurale)
        put("dernierElapsedRealtime", dernierElapsedRealtime)
        put("zoneActive", zoneActive)
        put("niveauArrosoir", niveauArrosoir)
    }

    private fun GardenPlotEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("etat", etat)
        put("solId", solId)
        put("areneRequise", areneRequise)
        put("deblocage", deblocage)
        put("terrain", terrain)
        put("chantierFinMillis", chantierFinMillis)
        put("humidite", humidite.toDouble())
        put("dernierCalculHumidite", dernierCalculHumidite)
    }

    private fun GardenCropEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("plotId", plotId)
        put("seedId", seedId)
        put("plantedAtMillis", plantedAtMillis)
        put("minutesCumulees", minutesCumulees)
        put("dernierArrosageMillis", dernierArrosageMillis)
        put("arrosages", arrosages)
        put("revisionsPendantCulture", revisionsPendantCulture)
        put("recoltee", recoltee)
    }

    private fun GardenCrateEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("seedId", seedId)
        put("qualite", qualite)
        put("creeALeMillis", creeALeMillis)
    }

    private fun GardenInventoryEntity.enJson() = JSONObject().apply {
        put("cle", cle)
        put("seedId", seedId)
        put("qualite", qualite)
        put("quantite", quantite)
    }

    private fun GardenMimoEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("nom", nom)
        put("embaucheMillis", embaucheMillis)
    }

    private fun MemoChallengeEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("profileId", profileId)
        put("lineId", lineId)
        put("texte", texte)
        put("nomModule", nomModule)
        put("envoyeALeMillis", envoyeALeMillis)
        put("reclame", reclame)
    }

    private fun ChestEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("slotIndex", slotIndex)
        put("unlocksAtMillis", unlocksAtMillis)
        put("isOpened", isOpened)
        put("createdAt", createdAt)
    }

    private fun ChallengeEntity.enJson() = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("title", title)
        put("description", description)
        put("targetAmount", targetAmount)
        put("currentProgress", currentProgress)
        put("rewardCoins", rewardCoins)
        put("rewardXp", rewardXp)
        put("rewardChestType", rewardChestType)
        put("isClaimed", isClaimed)
        put("resetDate", resetDate)
    }

    private fun JSONObject.versUser(base: UserEntity = UserEntity()) = base.copy(
        id = optLong("id", base.id),
        pseudo = optString("pseudo", base.pseudo),
        level = optInt("level", base.level),
        xp = optInt("xp", base.xp),
        xpNext = optInt("xpNext", base.xpNext),
        coins = optInt("coins", base.coins),
        gems = optInt("gems", base.gems),
        streakDays = optInt("streakDays", base.streakDays),
        lastLoginDate = optString("lastLoginDate", base.lastLoginDate),
        totalFocusMinutes = optInt("totalFocusMinutes", base.totalFocusMinutes),
        totalAdsWatched = optInt("totalAdsWatched", base.totalAdsWatched),
        totalChestsOpened = optInt("totalChestsOpened", base.totalChestsOpened),
        equippedThemeId = optString("equippedThemeId", base.equippedThemeId),
        unlockedThemeIds = optString("unlockedThemeIds", base.unlockedThemeIds),
        moduleSlots = optInt("moduleSlots", base.moduleSlots),
        focusSlots = optInt("focusSlots", base.focusSlots),
        memoProfileSlots = optInt("memoProfileSlots", base.memoProfileSlots),
        adCountToday = optInt("adCountToday", base.adCountToday),
        lastAdDate = optString("lastAdDate", base.lastAdDate),
        totalCoinsEarned = optInt("totalCoinsEarned", base.totalCoinsEarned),
        totalCoinsSpent = optInt("totalCoinsSpent", base.totalCoinsSpent),
        bestStreak = optInt("bestStreak", base.bestStreak),
        streakShields = optInt("streakShields", base.streakShields),
        lastDailyChestDay = optString("lastDailyChestDay", base.lastDailyChestDay)
    )

    private fun JSONObject.versDayRecord(base: DayRecordEntity = DayRecordEntity()) = base.copy(
        date = optString("date", base.date),
        status = optString("status", base.status),
        note = optString("note", base.note)
    )

    private fun JSONObject.versStats(base: StatsEntity = StatsEntity()) = base.copy(
        date = optString("date", base.date),
        xpGained = optInt("xpGained", base.xpGained),
        coinsGained = optInt("coinsGained", base.coinsGained),
        coinsSpent = optInt("coinsSpent", base.coinsSpent),
        focusSessions = optInt("focusSessions", base.focusSessions),
        focusMinutes = optInt("focusMinutes", base.focusMinutes),
        chestsOpened = optInt("chestsOpened", base.chestsOpened),
        adsWatched = optInt("adsWatched", base.adsWatched),
        memoLinesReceived = optInt("memoLinesReceived", base.memoLinesReceived)
    )

    private fun JSONObject.versArenaReward(
        base: ArenaRewardEntity = ArenaRewardEntity()
    ) = base.copy(
        arenaId = optInt("arenaId", base.arenaId),
        claimedAt = optLong("claimedAt", base.claimedAt)
    )

    private fun JSONObject.versObjective(base: ObjectiveEntity = ObjectiveEntity()) = base.copy(
        id = optLong("id", base.id),
        text = optString("text", base.text),
        isDone = optBoolean("isDone", base.isDone),
        createdAt = optLong("createdAt", base.createdAt),
        completedAt = optLong("completedAt", base.completedAt)
    )

    private fun JSONObject.versMemoProfile(
        base: MemoProfileEntity = MemoProfileEntity()
    ) = base.copy(
        id = optLong("id", base.id),
        name = optString("name", base.name),
        frequencyPerDay = optInt("frequencyPerDay", base.frequencyPerDay),
        scheduledHour = optInt("scheduledHour", base.scheduledHour),
        scheduledMinute = optInt("scheduledMinute", base.scheduledMinute),
        isActive = optBoolean("isActive", base.isActive),
        sentLineHistory = optString("sentLineHistory", base.sentLineHistory),
        lastNotifiedAtMillis = optLong("lastNotifiedAtMillis", base.lastNotifiedAtMillis),
        randomMode = optBoolean("randomMode", base.randomMode),
        randomStartHour = optInt("randomStartHour", base.randomStartHour),
        randomStartMinute = optInt("randomStartMinute", base.randomStartMinute),
        randomEndHour = optInt("randomEndHour", base.randomEndHour),
        randomEndMinute = optInt("randomEndMinute", base.randomEndMinute),
        activeDays = optString("activeDays", base.activeDays),
        nextTriggerAtMillis = optLong("nextTriggerAtMillis", base.nextTriggerAtMillis)
    )

    private fun JSONObject.versMemoLine(base: MemoLineEntity = MemoLineEntity()) = base.copy(
        id = optLong("id", base.id),
        profileId = optLong("profileId", base.profileId),
        text = optString("text", base.text),
        orderIndex = optInt("orderIndex", base.orderIndex),
        box = optInt("box", base.box),
        nextReviewAtMillis = optLong("nextReviewAtMillis", base.nextReviewAtMillis),
        reviewCount = optInt("reviewCount", base.reviewCount),
        successCount = optInt("successCount", base.successCount)
    )

    private fun JSONObject.versGardenState(
        base: GardenStateEntity = GardenStateEntity()
    ) = base.copy(
        id = optLong("id", base.id),
        eau = optInt("eau", base.eau),
        compost = optInt("compost", base.compost),
        cristaux = optInt("cristaux", base.cristaux),
        gouttes = optInt("gouttes", base.gouttes),
        eauGagneeAujourdhui = optInt("eauGagneeAujourdhui", base.eauGagneeAujourdhui),
        jourPlafond = optString("jourPlafond", base.jourPlafond),
        revisionsDepuisOuverture = optInt(
            "revisionsDepuisOuverture",
            base.revisionsDepuisOuverture
        ),
        derniereHeureMurale = optLong("derniereHeureMurale", base.derniereHeureMurale),
        dernierElapsedRealtime = optLong(
            "dernierElapsedRealtime",
            base.dernierElapsedRealtime
        ),
        zoneActive = optString("zoneActive", base.zoneActive),
        niveauArrosoir = optInt("niveauArrosoir", base.niveauArrosoir)
    )

    private fun JSONObject.versGardenPlot(base: GardenPlotEntity = GardenPlotEntity()) = base.copy(
        id = optInt("id", base.id),
        etat = optString("etat", base.etat),
        solId = optString("solId", base.solId),
        areneRequise = optInt("areneRequise", base.areneRequise),
        deblocage = optString("deblocage", base.deblocage),
        terrain = optString("terrain", base.terrain),
        chantierFinMillis = optLong("chantierFinMillis", base.chantierFinMillis),
        humidite = optDouble("humidite", base.humidite.toDouble()).toFloat(),
        dernierCalculHumidite = optLong(
            "dernierCalculHumidite",
            base.dernierCalculHumidite
        )
    )

    private fun JSONObject.versGardenCrop(base: GardenCropEntity = GardenCropEntity()) = base.copy(
        id = optLong("id", base.id),
        plotId = optInt("plotId", base.plotId),
        seedId = optString("seedId", base.seedId),
        plantedAtMillis = optLong("plantedAtMillis", base.plantedAtMillis),
        minutesCumulees = optLong("minutesCumulees", base.minutesCumulees),
        dernierArrosageMillis = optLong(
            "dernierArrosageMillis",
            base.dernierArrosageMillis
        ),
        arrosages = optInt("arrosages", base.arrosages),
        revisionsPendantCulture = optInt(
            "revisionsPendantCulture",
            base.revisionsPendantCulture
        ),
        recoltee = optBoolean("recoltee", base.recoltee)
    )

    private fun JSONObject.versGardenCrate(
        base: GardenCrateEntity = GardenCrateEntity()
    ) = base.copy(
        id = optLong("id", base.id),
        seedId = optString("seedId", base.seedId),
        qualite = optString("qualite", base.qualite),
        creeALeMillis = optLong("creeALeMillis", base.creeALeMillis)
    )

    private fun JSONObject.versGardenInventory(
        base: GardenInventoryEntity = GardenInventoryEntity()
    ) = base.copy(
        cle = optString("cle", base.cle),
        seedId = optString("seedId", base.seedId),
        qualite = optString("qualite", base.qualite),
        quantite = optInt("quantite", base.quantite)
    )

    private fun JSONObject.versGardenMimo(
        base: GardenMimoEntity = GardenMimoEntity()
    ) = base.copy(
        id = optLong("id", base.id),
        type = optString("type", base.type),
        nom = optString("nom", base.nom),
        embaucheMillis = optLong("embaucheMillis", base.embaucheMillis)
    )

    private fun JSONObject.versMemoChallenge(
        base: MemoChallengeEntity = MemoChallengeEntity()
    ) = base.copy(
        id = optLong("id", base.id),
        profileId = optLong("profileId", base.profileId),
        lineId = optLong("lineId", base.lineId),
        texte = optString("texte", base.texte),
        nomModule = optString("nomModule", base.nomModule),
        envoyeALeMillis = optLong("envoyeALeMillis", base.envoyeALeMillis),
        reclame = optBoolean("reclame", base.reclame)
    )

    private fun JSONObject.versChest(base: ChestEntity = ChestEntity()) = base.copy(
        id = optLong("id", base.id),
        type = optString("type", base.type),
        slotIndex = optInt("slotIndex", base.slotIndex),
        unlocksAtMillis = optLong("unlocksAtMillis", base.unlocksAtMillis),
        isOpened = optBoolean("isOpened", base.isOpened),
        createdAt = optLong("createdAt", base.createdAt)
    )

    private fun JSONObject.versChallenge(
        base: ChallengeEntity = ChallengeEntity()
    ) = base.copy(
        id = optString("id", base.id),
        type = optString("type", base.type),
        title = optString("title", base.title),
        description = optString("description", base.description),
        targetAmount = optInt("targetAmount", base.targetAmount),
        currentProgress = optInt("currentProgress", base.currentProgress),
        rewardCoins = optInt("rewardCoins", base.rewardCoins),
        rewardXp = optInt("rewardXp", base.rewardXp),
        rewardChestType = optString("rewardChestType", base.rewardChestType),
        isClaimed = optBoolean("isClaimed", base.isClaimed),
        resetDate = optString("resetDate", base.resetDate)
    )

    /**
     * Lit l'archive en mémoire, en écartant les chemins dangereux.
     *
     * Le contrôle porte sur chaque entrée avant qu'elle ne soit lue : une
     * archive vient de l'extérieur, et une entrée nommée `../../databases/…`
     * n'a rien à faire ici.
     */
    private fun lireArchive(source: Uri): Map<String, ByteArray> {
        contexte.contentResolver.openInputStream(source)?.use { flux ->
            return BoundedZipReader.read(
                source = flux,
                limits = BoundedZipReader.Limits(
                    maxTotalBytes = MAX_OCTETS_ARCHIVE,
                    maxEntryBytes = MAX_OCTETS_PAR_ENTREE,
                    maxEntries = MAX_ENTREES_ARCHIVE
                ),
                accepter = SauvegardeEngine::cheminSur
            )
        } ?: error("Impossible de lire ce fichier.")
    }

    companion object {
        private const val MAX_OCTETS_ARCHIVE = 64L * 1024 * 1024
        private const val MAX_OCTETS_PAR_ENTREE = 48L * 1024 * 1024
        private const val MAX_ENTREES_ARCHIVE = 32

        fun nomProposé(): String =
            SauvegardeEngine.nomFichier(LocalDate.now().toString())
    }
}
