package com.sankailife.core.modules

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sankailife.core.data.archive.BoundedZipReader
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.MemoLineEntity
import com.sankailife.core.data.db.entities.MemoProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Lecture et installation d'un module d'apprentissage.
 *
 * Deux temps séparés, et c'est délibéré : [inspecter] ne touche à rien,
 * [installer] écrit. Un module vient de l'extérieur et personne ne doit
 * découvrir son contenu après l'avoir installé.
 */
class ModuleRepository(
    private val contexte: Context,
    private val db: SankaiDatabase
) {

    /**
     * Lit un module sans rien modifier.
     *
     * Renvoie le verdict et, s'il est utilisable, les cartes déjà nettoyées :
     * les relire à l'installation ferait travailler deux fois et, surtout,
     * laisserait la porte ouverte à un contenu différent entre les deux
     * lectures.
     */
    suspend fun inspecter(source: Uri): Pair<ModuleEngine.Verdict, List<String>> =
        withContext(Dispatchers.IO) {
            val contenu = lireArchive(source)
            val octets = contenu.values.sumOf { it.size.toLong() }

            val manifeste = trouver(contenu, "module.json")?.let { octets ->
                runCatching {
                    val o = JSONObject(String(octets))
                    ModuleEngine.Manifeste(
                        schemaVersion = o.optInt("schemaVersion", 0),
                        id = o.optString("id"),
                        nom = o.optString("name"),
                        version = o.optString("version"),
                        langue = o.optString("language"),
                        langueSource = o.optString("sourceLanguage"),
                        auteur = o.optString("author"),
                        description = o.optString("description"),
                        licence = o.optString("license")
                    )
                }.getOrNull()
            }

            val nomFichier = manifeste?.let {
                runCatching {
                    JSONObject(String(trouver(contenu, "module.json")!!))
                        .optString("flashcardsFile", "flashcards.txt")
                }.getOrDefault("flashcards.txt")
            } ?: "flashcards.txt"

            val cartes = trouver(contenu, nomFichier)
                ?.let { String(it) }
                ?.lines()
                ?.mapNotNull { ModuleEngine.nettoyerLigne(it) }
                .orEmpty()

            ModuleEngine.verifier(manifeste, cartes.size, octets) to cartes
        }

    /**
     * Installe un module déjà inspecté.
     *
     * @return le nom réellement donné au module, qui peut différer du nom
     *   demandé si un module homonyme existait déjà.
     */
    suspend fun installer(
        manifeste: ModuleEngine.Manifeste,
        cartes: List<String>
    ): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dao = db.memoDao()
            val existants = dao.getAllProfilesOnce().map { it.name }.toSet()
            val nom = ModuleEngine.nomInstallation(manifeste.nom, existants)

            val id = dao.upsertProfile(
                MemoProfileEntity(
                    name = nom,
                    // Inactif à l'installation : un module importé ne doit pas se
                    // mettre à envoyer des notifications sans qu'on l'ait décidé.
                    isActive = false,
                    // Reprise telle quelle du manifeste : c'est l'auteur du
                    // module qui sait dans quelle langue son contenu se dit.
                    langue = manifeste.langue
                )
            )

            cartes.forEachIndexed { index, texte ->
                dao.insertLine(
                    MemoLineEntity(profileId = id, text = texte, orderIndex = index)
                )
            }
            nom
        }
    }

    /**
     * Cherche un fichier dans l'archive, quel que soit son dossier parent.
     *
     * Compresser un dossier ajoute son nom devant chaque entrée selon l'outil
     * utilisé. Exiger `module.json` à la racine ferait échouer un module
     * parfaitement valide pour une raison que personne ne comprendrait.
     */

    /**
     * Lit une archive contenant **plusieurs** modules.
     *
     * C'est ce qui permet de partager un parcours entier en un fichier : les
     * six niveaux de portugais dans une archive, chacun dans son dossier, plus
     * un `collection.json` qui dit dans quel ordre les suivre.
     *
     * Rend la liste des modules trouvés, dans l'ordre déclaré par la collection
     * quand elle en déclare un — un parcours installé dans le désordre
     * afficherait le C2 avant le A1.
     */
    suspend fun inspecterCollection(
        source: Uri
    ): List<Pair<ModuleEngine.Manifeste, List<String>>> = withContext(Dispatchers.IO) {
        val contenu = lireArchive(source)

        val ordre = trouver(contenu, "collection.json")?.let { octets ->
            runCatching {
                val tableau = JSONObject(String(octets)).getJSONArray("modules")
                (0 until tableau.length()).map { tableau.getJSONObject(it).optString("id") }
            }.getOrNull()
        }.orEmpty()

        // Chaque dossier qui porte un module.json est un module.
        val dossiers = contenu.keys
            .filter { it.substringAfterLast('/') == "module.json" && it.contains('/') }
            .map { it.substringBeforeLast('/') }
            .distinct()

        val tries = if (ordre.isEmpty()) dossiers.sorted()
        else dossiers.sortedBy { d ->
            ordre.indexOf(d).let { if (it < 0) Int.MAX_VALUE else it }
        }

        tries.mapNotNull { dossier ->
            val manifeste = trouver(contenu, "$dossier/module.json")?.let { octets ->
                runCatching { manifesteDepuis(String(octets)) }.getOrNull()
            } ?: return@mapNotNull null

            val nomCartes = runCatching {
                JSONObject(String(trouver(contenu, "$dossier/module.json")!!))
                    .optString("flashcardsFile", "flashcards.txt")
            }.getOrDefault("flashcards.txt")

            val cartes = trouver(contenu, "$dossier/$nomCartes")
                ?.let { String(it) }
                ?.lines()
                ?.mapNotNull { ModuleEngine.nettoyerLigne(it) }
                .orEmpty()

            if (cartes.isEmpty()) null else manifeste to cartes
        }
    }

    private fun manifesteDepuis(json: String): ModuleEngine.Manifeste {
        val o = JSONObject(json)
        return ModuleEngine.Manifeste(
            schemaVersion = o.optInt("schemaVersion", 0),
            id = o.optString("id"),
            nom = o.optString("name"),
            version = o.optString("version"),
            langue = o.optString("language"),
            langueSource = o.optString("sourceLanguage"),
            auteur = o.optString("author"),
            description = o.optString("description"),
            licence = o.optString("license")
        )
    }

    private fun trouver(contenu: Map<String, ByteArray>, nom: String): ByteArray? =
        contenu[nom] ?: contenu.entries
            .firstOrNull { it.key.substringAfterLast('/') == nom }
            ?.value

    private fun lireArchive(source: Uri): Map<String, ByteArray> {
        contexte.contentResolver.openInputStream(source)?.use { flux ->
            return BoundedZipReader.read(
                source = flux,
                limits = BoundedZipReader.Limits(
                    maxTotalBytes = ModuleEngine.MAX_OCTETS.toLong(),
                    maxEntryBytes = ModuleEngine.MAX_OCTETS.toLong(),
                    maxEntries = MAX_ENTREES_ARCHIVE
                ),
                accepter = ModuleEngine::cheminSur
            )
        } ?: error("Impossible de lire ce fichier.")
    }

    private companion object {
        const val MAX_ENTREES_ARCHIVE = 128
    }
}
