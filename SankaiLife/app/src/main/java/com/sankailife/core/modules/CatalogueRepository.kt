package com.sankailife.core.modules

import android.content.Context
import com.sankailife.core.data.db.SankaiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Télécharge et installe les modules du catalogue.
 *
 * Le contrat tient en une phrase : **on ne télécharge que ce qu'on a demandé,
 * et une fois téléchargé, plus rien n'a besoin du réseau.** Les cartes entrent
 * dans la base, le fichier temporaire est effacé, et le module fonctionne
 * ensuite comme s'il avait toujours été là.
 */
class CatalogueRepository(
    private val contexte: Context,
    private val db: SankaiDatabase
) {
    private val modules = ModuleRepository(contexte, db)

    /** Résultat d'une opération, avec de quoi l'expliquer. */
    sealed interface Issue {
        data class Ok(val message: String) : Issue
        data class Echec(val raison: String) : Issue
    }

    /**
     * Récupère le catalogue.
     *
     * Aucune mise en cache : le catalogue pèse quelques kilo-octets et change
     * rarement, mais un cache périmé afficherait des modules retirés et
     * cacherait les nouveaux — pour un gain de quelques centaines de
     * millisecondes.
     */
    suspend fun catalogue(): Result<List<CatalogueEngine.Entree>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val texte = lireTexte(URL_CATALOGUE)
                val racine = JSONObject(texte)
                val version = racine.optInt("schemaVersion", 0)
                require(version <= CatalogueEngine.VERSION_SCHEMA) {
                    "Ce catalogue demande une version plus récente de l'application."
                }
                val tableau = racine.getJSONArray("modules")
                (0 until tableau.length()).map { i ->
                    val o = tableau.getJSONObject(i)
                    CatalogueEngine.Entree(
                        id = o.optString("id"),
                        nom = o.optString("nom"),
                        description = o.optString("description"),
                        langue = o.optString("langue"),
                        niveau = o.optString("niveau"),
                        cartes = o.optInt("cartes"),
                        octets = o.optLong("octets"),
                        empreinte = o.optString("sha256"),
                        licence = o.optString("licence"),
                        auteur = o.optString("auteur"),
                        url = o.optString("url")
                    )
                }
            }.recoverCatching { throw IllegalStateException(messageLisible(it), it) }
        }

    /**
     * Télécharge puis installe un module.
     *
     * L'ordre compte : on vérifie l'entrée, on télécharge dans un fichier
     * temporaire, on contrôle taille **et** empreinte, on inspecte le contenu,
     * et seulement alors on écrit en base. Chaque étape peut refuser sans avoir
     * rien modifié.
     */
    suspend fun installer(entree: CatalogueEngine.Entree): Issue =
        withContext(Dispatchers.IO) {
            CatalogueEngine.refus(entree)?.let { return@withContext Issue.Echec(it) }

            val temporaire = File(contexte.cacheDir, "module-${entree.id}.zip")
            try {
                val octets = telecharger(entree.url, temporaire)
                    ?: return@withContext Issue.Echec("Téléchargement impossible.")

                CatalogueEngine.verifierRecu(entree, octets, empreinte(temporaire))
                    ?.let { return@withContext Issue.Echec(it) }

                val (verdict, cartes) = modules.inspecter(
                    android.net.Uri.fromFile(temporaire)
                )
                when (verdict) {
                    is ModuleEngine.Verdict.Refuse ->
                        Issue.Echec(verdict.raison)
                    is ModuleEngine.Verdict.Utilisable -> {
                        val nom = modules.installer(verdict.apercu.manifeste, cartes)
                        Issue.Ok("« $nom » installé — ${cartes.size} cartes, hors ligne.")
                    }
                }
            } catch (e: Throwable) {
                Issue.Echec(messageLisible(e))
            } finally {
                // Le paquet ne sert plus : les cartes sont en base. Le garder
                // occuperait de la place pour un fichier que rien ne relira.
                temporaire.delete()
            }
        }

    // --- Réseau ---------------------------------------------------------------

    /**
     * Écrit la réponse dans un fichier et rend sa taille.
     *
     * Passer par un fichier plutôt que par la mémoire : un paquet corrompu
     * annonçant une taille délirante ne doit pas pouvoir remplir la mémoire de
     * l'application avant d'être refusé.
     */
    private fun telecharger(url: String, destination: File): Long? {
        verifierHote(url)?.let { throw IllegalArgumentException(it) }
        val connexion = ouvrir(url)
        if (connexion.responseCode !in 200..299) {
            throw IllegalStateException("Le serveur a répondu ${connexion.responseCode}.")
        }
        var total = 0L
        connexion.inputStream.use { entree ->
            destination.outputStream().use { sortie ->
                val tampon = ByteArray(8 * 1024)
                while (true) {
                    val lus = entree.read(tampon)
                    if (lus <= 0) break
                    total += lus
                    if (total > CatalogueEngine.MAX_OCTETS) {
                        throw IllegalStateException("Fichier plus gros qu'annoncé.")
                    }
                    sortie.write(tampon, 0, lus)
                }
            }
        }
        return total
    }

    private fun empreinte(fichier: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fichier.inputStream().use { flux ->
            val tampon = ByteArray(8 * 1024)
            while (true) {
                val lus = flux.read(tampon)
                if (lus <= 0) break
                digest.update(tampon, 0, lus)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun lireTexte(url: String): String {
        verifierHote(url)?.let { throw IllegalArgumentException(it) }
        return ouvrir(url).inputStream.bufferedReader().use { it.readText() }
    }

    private fun ouvrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = DELAI_MS
            readTimeout = DELAI_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SankaiLife")
        }

    /**
     * N'accepte que les hôtes du dépôt.
     *
     * Le catalogue est un fichier distant qui contient des adresses : sans
     * cette barrière, une entrée modifiée pourrait faire télécharger n'importe
     * quoi depuis n'importe où.
     */
    private fun verifierHote(url: String): String? {
        val hote = runCatching { URL(url).host }.getOrNull()
        return if (hote != null && HOTES.any { hote == it || hote.endsWith(".$it") }) null
        else "Source non autorisée."
    }

    private fun messageLisible(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Pas de connexion internet."
        is java.net.SocketTimeoutException -> "Le serveur ne répond pas."
        is java.io.FileNotFoundException -> "Module introuvable sur le dépôt."
        else -> e.message ?: "Erreur inconnue."
    }

    companion object {
        private const val DEPOT = "N7T0-OF/sankai-life"
        const val URL_CATALOGUE =
            "https://raw.githubusercontent.com/$DEPOT/main/modules/catalogue.json"

        private val HOTES = setOf(
            "raw.githubusercontent.com", "github.com", "objects.githubusercontent.com"
        )

        private const val DELAI_MS = 15_000
    }
}
