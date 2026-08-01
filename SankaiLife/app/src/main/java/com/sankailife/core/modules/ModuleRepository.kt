package com.sankailife.core.modules

import android.content.Context
import android.net.Uri
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.db.entities.MemoLineEntity
import com.sankailife.core.data.db.entities.MemoProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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
        val existants = db.memoDao().getAllProfilesOnce().map { it.name }.toSet()
        val nom = ModuleEngine.nomInstallation(manifeste.nom, existants)

        val id = db.memoDao().upsertProfile(
            MemoProfileEntity(
                name = nom,
                // Inactif à l'installation : un module importé ne doit pas se
                // mettre à envoyer des notifications sans qu'on l'ait décidé.
                isActive = false
            )
        )

        cartes.forEach { texte ->
            db.memoDao().insertLine(MemoLineEntity(profileId = id, text = texte))
        }
        nom
    }

    /**
     * Cherche un fichier dans l'archive, quel que soit son dossier parent.
     *
     * Compresser un dossier ajoute son nom devant chaque entrée selon l'outil
     * utilisé. Exiger `module.json` à la racine ferait échouer un module
     * parfaitement valide pour une raison que personne ne comprendrait.
     */
    private fun trouver(contenu: Map<String, ByteArray>, nom: String): ByteArray? =
        contenu[nom] ?: contenu.entries
            .firstOrNull { it.key.substringAfterLast('/') == nom }
            ?.value

    private fun lireArchive(source: Uri): Map<String, ByteArray> {
        val resultat = linkedMapOf<String, ByteArray>()
        contexte.contentResolver.openInputStream(source)?.use { flux ->
            ZipInputStream(flux).use { zip ->
                var entree: ZipEntry? = zip.nextEntry
                var total = 0L
                while (entree != null) {
                    val nom = entree.name
                    if (!entree.isDirectory && ModuleEngine.cheminSur(nom)) {
                        val tampon = ByteArrayOutputStream()
                        zip.copyTo(tampon)
                        total += tampon.size()
                        // La borne est vérifiée pendant la lecture, pas après :
                        // une archive gonflée volontairement remplirait la
                        // mémoire avant qu'on ait pu la refuser.
                        if (total > ModuleEngine.MAX_OCTETS) {
                            error("Fichier trop lourd.")
                        }
                        resultat[nom] = tampon.toByteArray()
                    }
                    zip.closeEntry()
                    entree = zip.nextEntry
                }
            }
        } ?: error("Impossible de lire ce fichier.")
        return resultat
    }
}
