package com.sankailife.core.data.sauvegarde

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sankailife.BuildConfig
import com.sankailife.core.data.db.SankaiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        val sections = collecter()

        // Le contenu est assemblé en mémoire d'abord : l'empreinte doit porter
        // sur ce qui sera réellement écrit, et on ne peut pas la calculer sur
        // un flux déjà parti.
        val fichiers = sections.mapValues { (_, json) -> json.toString().toByteArray() }
        val empreinte = SauvegardeEngine.empreinte(
            fichiers.toSortedMap().values.fold(ByteArray(0)) { acc, b -> acc + b }
        )

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
                val entete = manifeste.toString(2).toByteArray()
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
        val resultat = linkedMapOf<String, JSONObject>()

        db.userDao().getUserOnce()?.let { u ->
            resultat[SauvegardeEngine.Section.PROFIL.cle] = JSONObject().apply {
                put("pseudo", u.pseudo)
                put("level", u.level)
                put("xp", u.xp)
                put("xpNext", u.xpNext)
                put("coins", u.coins)
                put("gems", u.gems)
                put("streakDays", u.streakDays)
                put("bestStreak", u.bestStreak)
                put("streakShields", u.streakShields)
                put("totalFocusMinutes", u.totalFocusMinutes)
                put("totalChestsOpened", u.totalChestsOpened)
                put("moduleSlots", u.moduleSlots)
                put("focusSlots", u.focusSlots)
            }
        }

        // Mémos et cartes, avec leur état de révision : c'est le travail
        // d'apprentissage lui-même, la donnée la plus difficile à reconstituer.
        val profils = db.memoDao().getAllProfilesOnce()
        resultat[SauvegardeEngine.Section.MEMOS.cle] = JSONObject().apply {
            put("profils", JSONArray().apply {
                profils.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("isActive", p.isActive)
                        put("frequencyPerDay", p.frequencyPerDay)
                        put("scheduledHour", p.scheduledHour)
                        put("scheduledMinute", p.scheduledMinute)
                        put("lignes", JSONArray().apply {
                            db.memoDao().getLinesOnce(p.id).forEach { l ->
                                put(JSONObject().apply {
                                    put("text", l.text)
                                    put("box", l.box)
                                    put("nextReviewAtMillis", l.nextReviewAtMillis)
                                })
                            }
                        })
                    })
                }
            })
        }

        db.gardenDao().etat()?.let { g ->
            resultat[SauvegardeEngine.Section.JARDIN.cle] = JSONObject().apply {
                put("eau", g.eau)
                put("compost", g.compost)
                put("cristaux", g.cristaux)
                put("niveauArrosoir", g.niveauArrosoir)
                put("parcelles", JSONArray().apply {
                    db.gardenDao().parcelles().forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("etat", p.etat)
                            put("solId", p.solId)
                            put("deblocage", p.deblocage)
                            put("terrain", p.terrain)
                            put("humidite", p.humidite.toDouble())
                        })
                    }
                })
                put("mimos", JSONArray().apply {
                    db.gardenDao().mimos().forEach { m ->
                        put(JSONObject().apply {
                            put("type", m.type)
                            put("nom", m.nom)
                        })
                    }
                })
            }
        }

        return resultat
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
        val contenu = lireArchive(source)
        val manifesteBrut = contenu["manifest.json"]

        val manifeste = manifesteBrut?.let {
            runCatching {
                val o = JSONObject(String(it))
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
            contenu.filterKeys { it != "manifest.json" }
                .toSortedMap()
                .values.fold(ByteArray(0)) { acc, b -> acc + b }
        )

        val verdict = SauvegardeEngine.verifier(manifeste, empreinte, BuildConfig.VERSION_CODE)
        val sections = manifeste?.sections.orEmpty()
            .mapNotNull { cle -> SauvegardeEngine.Section.entries.firstOrNull { it.cle == cle } }

        val cartes = contenu["memos.json"]?.let { octets ->
            runCatching {
                val profils = JSONObject(String(octets)).getJSONArray("profils")
                (0 until profils.length()).sumOf { i ->
                    profils.getJSONObject(i).getJSONArray("lignes").length()
                }
            }.getOrDefault(0)
        } ?: 0

        Apercu(verdict, sections, cartes)
    }

    /**
     * Restaure les sections demandées.
     *
     * Une sauvegarde de sécurité est écrite avant toute modification, et toute
     * la restauration tient dans une transaction : une erreur au milieu laisse
     * la base exactement dans l'état d'avant. Sans ces deux protections, un
     * fichier abîmé à moitié lu détruirait le profil sans recours.
     *
     * @return les sections effectivement restaurées.
     */
    suspend fun restaurer(
        source: Uri,
        demandees: Set<SauvegardeEngine.Section>,
        sauvegardeDeSecurite: Uri?
    ): List<SauvegardeEngine.Section> = withContext(Dispatchers.IO) {
        val contenu = lireArchive(source)
        val apercu = inspecter(source)
        val verdict = apercu.verdict
        if (verdict !is SauvegardeEngine.Verdict.Utilisable) {
            error((verdict as SauvegardeEngine.Verdict.Refuse).raison)
        }

        sauvegardeDeSecurite?.let { runCatching { exporter(it) } }

        val aFaire = SauvegardeEngine.sectionsARestaurer(
            verdict.manifeste.sections, demandees
        )

        db.withTransaction {
            aFaire.forEach { section ->
                val octets = contenu["${section.cle}.json"] ?: return@forEach
                when (section) {
                    SauvegardeEngine.Section.PROFIL -> restaurerProfil(JSONObject(String(octets)))
                    SauvegardeEngine.Section.MEMOS -> restaurerMemos(JSONObject(String(octets)))
                    SauvegardeEngine.Section.JARDIN -> restaurerJardin(JSONObject(String(octets)))
                    else -> Unit
                }
            }
        }
        aFaire
    }

    private suspend fun restaurerProfil(o: JSONObject) {
        val actuel = db.userDao().getUserOnce() ?: return
        db.userDao().upsert(
            actuel.copy(
                pseudo = o.optString("pseudo", actuel.pseudo),
                level = o.optInt("level", actuel.level),
                xp = o.optInt("xp", actuel.xp),
                xpNext = o.optInt("xpNext", actuel.xpNext),
                coins = o.optInt("coins", actuel.coins),
                gems = o.optInt("gems", actuel.gems),
                streakDays = o.optInt("streakDays", actuel.streakDays),
                bestStreak = o.optInt("bestStreak", actuel.bestStreak),
                streakShields = o.optInt("streakShields", actuel.streakShields),
                totalFocusMinutes = o.optInt("totalFocusMinutes", actuel.totalFocusMinutes),
                totalChestsOpened = o.optInt("totalChestsOpened", actuel.totalChestsOpened),
                moduleSlots = o.optInt("moduleSlots", actuel.moduleSlots),
                focusSlots = o.optInt("focusSlots", actuel.focusSlots)
            )
        )
    }

    /**
     * Restaure les mémos en **ajoutant**, jamais en écrasant.
     *
     * Un module portant le même nom qu'un module existant est importé comme
     * copie. Remplacer par erreur détruit un travail ; ajouter par erreur ne
     * fait que créer un doublon, qui se supprime en deux gestes.
     */
    private suspend fun restaurerMemos(o: JSONObject) {
        val existants = db.memoDao().getAllProfilesOnce().map { it.name }.toSet()
        val profils = o.optJSONArray("profils") ?: return

        for (i in 0 until profils.length()) {
            val p = profils.getJSONObject(i)
            val nomSouhaite = p.optString("name", "Mémo importé")
            val nom = if (nomSouhaite in existants) "$nomSouhaite (importé)" else nomSouhaite

            val id = db.memoDao().upsertProfile(
                com.sankailife.core.data.db.entities.MemoProfileEntity(
                    name = nom,
                    isActive = false,
                    frequencyPerDay = p.optInt("frequencyPerDay", 3),
                    scheduledHour = p.optInt("scheduledHour", 18),
                    scheduledMinute = p.optInt("scheduledMinute", 0)
                )
            )

            val lignes = p.optJSONArray("lignes") ?: continue
            for (j in 0 until lignes.length()) {
                val l = lignes.getJSONObject(j)
                db.memoDao().insertLine(
                    com.sankailife.core.data.db.entities.MemoLineEntity(
                        profileId = id,
                        text = l.optString("text"),
                        box = l.optInt("box", 0),
                        nextReviewAtMillis = l.optLong("nextReviewAtMillis", 0L)
                    )
                )
            }
        }
    }

    private suspend fun restaurerJardin(o: JSONObject) {
        val etat = db.gardenDao().etat() ?: return
        db.gardenDao().sauverEtat(
            etat.copy(
                eau = o.optInt("eau", etat.eau),
                compost = o.optInt("compost", etat.compost),
                cristaux = o.optInt("cristaux", etat.cristaux),
                niveauArrosoir = o.optInt("niveauArrosoir", etat.niveauArrosoir)
            )
        )

        val parcelles = o.optJSONArray("parcelles") ?: return
        val existantes = db.gardenDao().parcelles().associateBy { it.id }
        val aEcrire = (0 until parcelles.length()).mapNotNull { i ->
            val p = parcelles.getJSONObject(i)
            val id = p.optInt("id", -1)
            if (id < 0) return@mapNotNull null
            (existantes[id] ?: com.sankailife.core.garden.data.GardenPlotEntity(id = id)).copy(
                etat = p.optString("etat", "EMPTY"),
                solId = p.optString("solId", "terre"),
                deblocage = p.optString("deblocage", "CACHEE"),
                terrain = p.optString("terrain", "ORDINAIRE"),
                humidite = p.optDouble("humidite", 0.5).toFloat()
            )
        }
        if (aEcrire.isNotEmpty()) db.gardenDao().sauverParcelles(aEcrire)
    }

    /**
     * Lit l'archive en mémoire, en écartant les chemins dangereux.
     *
     * Le contrôle porte sur chaque entrée avant qu'elle ne soit lue : une
     * archive vient de l'extérieur, et une entrée nommée `../../databases/…`
     * n'a rien à faire ici.
     */
    private fun lireArchive(source: Uri): Map<String, ByteArray> {
        val resultat = linkedMapOf<String, ByteArray>()
        contexte.contentResolver.openInputStream(source)?.use { flux ->
            ZipInputStream(flux).use { zip ->
                var entree: ZipEntry? = zip.nextEntry
                while (entree != null) {
                    val nom = entree.name
                    if (!entree.isDirectory && SauvegardeEngine.cheminSur(nom)) {
                        val tampon = ByteArrayOutputStream()
                        zip.copyTo(tampon)
                        resultat[nom] = tampon.toByteArray()
                    }
                    zip.closeEntry()
                    entree = zip.nextEntry
                }
            }
        } ?: error("Impossible de lire ce fichier.")
        return resultat
    }

    companion object {
        fun nomProposé(): String =
            SauvegardeEngine.nomFichier(LocalDate.now().toString())
    }
}
