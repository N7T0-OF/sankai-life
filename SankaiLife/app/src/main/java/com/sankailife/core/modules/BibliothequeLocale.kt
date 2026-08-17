package com.sankailife.core.modules

import android.content.Context
import com.sankailife.R
import com.sankailife.core.data.db.SankaiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Les contenus livrés avec l'application.
 *
 * **Remplace le catalogue distant, et c'était une erreur de le construire.**
 * La version précédente allait chercher les modules sur GitHub : téléchargement,
 * vérification d'empreinte, gestion du hors-ligne, hôtes autorisés. Beaucoup de
 * machinerie — et une dépendance réseau — pour 55 kilo-octets de cartes qui
 * tiennent sans peine dans l'installation.
 *
 * Tout est désormais embarqué. Aucune requête, aucun hôte à autoriser, aucun
 * message « pas de connexion » : les 1089 cartes sont déjà là, en mode avion
 * comme ailleurs. Le dépôt public reste utile pour versionner les sources et
 * recevoir des contributions ; l'application n'en dépend plus.
 */
class BibliothequeLocale(
    private val contexte: Context,
    private val db: SankaiDatabase
) {
    private val modules = ModuleRepository(contexte, db)

    /** Un contenu disponible sur l'appareil, sans rien télécharger. */
    data class Fiche(
        val id: String,
        val nom: String,
        val description: String = "",
        val langue: String = "",
        val niveau: String = "",
        val auteur: String = "",
        val licence: String = "",
        val cartes: Int = 0,
        val octets: Long = 0L,
        val fichier: String = "",
        /** Modules de la collection, vide pour un module seul. */
        val contenus: List<String> = emptyList(),
        val niveaux: List<String> = emptyList(),
        /** Collection à laquelle appartient un module, vide s'il est seul. */
        val collection: String = ""
    ) {
        val estCollection: Boolean get() = contenus.isNotEmpty()

        val details: String
            get() = buildList {
                if (estCollection) {
                    add("${contenus.size} niveaux")
                    val bornes = niveaux.filter { it.isNotBlank() }
                    if (bornes.size >= 2) add("${bornes.first()} → ${bornes.last()}")
                } else if (niveau.isNotBlank()) {
                    add(niveau)
                }
                add("$cartes cartes")
            }.joinToString(" · ")
    }

    data class Bibliotheque(
        val collections: List<Fiche> = emptyList(),
        val modules: List<Fiche> = emptyList()
    )

    sealed interface Issue {
        data class Ok(val message: String) : Issue
        data class Echec(val raison: String) : Issue
    }

    private companion object {
        const val DOSSIER = "modules"
        const val INDEX = "modules/index.json"
    }

    /**
     * Lit l'index embarqué.
     *
     * Aucune erreur réseau possible, donc aucun état d'erreur à afficher : si
     * l'index manque, c'est un défaut de compilation, pas une panne que
     * l'utilisateur pourrait résoudre.
     */
    suspend fun lire(): Bibliotheque = withContext(Dispatchers.IO) {
        runCatching {
            val texte = contexte.assets.open(INDEX).bufferedReader().use { it.readText() }
            val racine = JSONObject(texte)

            val collections = racine.optJSONArray("collections")?.let { t ->
                (0 until t.length()).map { i ->
                    val o = t.getJSONObject(i)
                    Fiche(
                        id = o.optString("id"),
                        nom = o.optString("nom"),
                        description = o.optString("description"),
                        langue = o.optString("langue"),
                        auteur = o.optString("auteur"),
                        cartes = o.optInt("cartes"),
                        octets = o.optLong("octets"),
                        fichier = o.optString("fichier"),
                        contenus = liste(o, "modules"),
                        niveaux = liste(o, "niveaux")
                    )
                }
            }.orEmpty()

            val fiches = racine.optJSONArray("modules")?.let { t ->
                (0 until t.length()).map { i ->
                    val o = t.getJSONObject(i)
                    Fiche(
                        id = o.optString("id"),
                        nom = o.optString("nom"),
                        description = o.optString("description"),
                        langue = o.optString("langue"),
                        niveau = o.optString("niveau"),
                        auteur = o.optString("auteur"),
                        licence = o.optString("licence"),
                        cartes = o.optInt("cartes"),
                        octets = o.optLong("octets"),
                        fichier = o.optString("fichier"),
                        collection = o.optString("collection")
                    )
                }
            }.orEmpty()

            Bibliotheque(collections, fiches)
        }.getOrElse { Bibliotheque() }
    }

    private fun liste(o: JSONObject, cle: String): List<String> =
        o.optJSONArray(cle)?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty()

    /**
     * Installe une fiche, collection ou module.
     *
     * Le paquet est copié dans le cache le temps d'être lu, puis effacé : les
     * lecteurs d'archive travaillent sur un fichier, et un fichier temporaire
     * de dix-huit kilo-octets ne mérite pas qu'on réécrive un lecteur.
     */
    suspend fun installer(fiche: Fiche): Issue = withContext(Dispatchers.IO) {
        val temporaire = File(contexte.cacheDir, "local-${fiche.id}.zip")
        try {
            contexte.assets.open("$DOSSIER/${fiche.fichier}").use { entree ->
                temporaire.outputStream().use { entree.copyTo(it) }
            }
            val uri = android.net.Uri.fromFile(temporaire)

            if (fiche.estCollection) {
                val trouves = modules.inspecterCollection(uri)
                if (trouves.isEmpty()) {
                    return@withContext Issue.Echec(contexte.getString(R.string.lib_collection_empty))
                }
                // Le niveau vient de l'ordre declare par la collection : c'est
                // elle qui sait que le troisieme module est le B1.
                val niveauxParId = fiche.contenus.zip(fiche.niveaux).toMap()
                var cartes = 0
                trouves.forEach { (manifeste, lignes) ->
                    modules.installer(
                        manifeste, lignes,
                        collection = fiche.id,
                        niveau = niveauxParId[manifeste.id].orEmpty()
                    )
                    cartes += lignes.size
                }
                Issue.Ok(
                    contexte.getString(R.string.lib_collection_installed, fiche.nom, trouves.size, cartes)
                )
            } else {
                val (verdict, cartes) = modules.inspecter(uri)
                when (verdict) {
                    is ModuleEngine.Verdict.Refuse -> Issue.Echec(verdict.raison)
                    is ModuleEngine.Verdict.Utilisable -> {
                        val nom = modules.installer(
                            verdict.apercu.manifeste, cartes,
                            collection = fiche.collection, niveau = fiche.niveau
                        )
                        Issue.Ok(
                            contexte.resources.getQuantityString(
                                R.plurals.lib_module_installed,
                                cartes.size,
                                nom,
                                cartes.size
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Issue.Echec(e.message ?: contexte.getString(R.string.lib_install_failed))
        } finally {
            temporaire.delete()
        }
    }

    /**
     * Installe seulement certains niveaux d'une collection.
     *
     * Obliger un débutant à installer le C2 pour accéder au A1 n'a pas de sens,
     * même quand tout tient dans le même fichier.
     */
    suspend fun installerNiveaux(
        collection: Fiche,
        idsVoulus: Set<String>
    ): Issue = withContext(Dispatchers.IO) {
        if (idsVoulus.isEmpty()) return@withContext Issue.Echec(contexte.getString(R.string.lib_no_level_chosen))
        val temporaire = File(contexte.cacheDir, "local-${collection.id}.zip")
        try {
            contexte.assets.open("$DOSSIER/${collection.fichier}").use { entree ->
                temporaire.outputStream().use { entree.copyTo(it) }
            }
            val trouves = modules
                .inspecterCollection(android.net.Uri.fromFile(temporaire))
                .filter { (manifeste, _) -> manifeste.id in idsVoulus }

            if (trouves.isEmpty()) {
                return@withContext Issue.Echec(contexte.getString(R.string.lib_levels_missing))
            }
            val niveauxParId = collection.contenus.zip(collection.niveaux).toMap()
            var cartes = 0
            trouves.forEach { (manifeste, lignes) ->
                modules.installer(
                    manifeste, lignes,
                    collection = collection.id,
                    niveau = niveauxParId[manifeste.id].orEmpty()
                )
                cartes += lignes.size
            }
            Issue.Ok(
                contexte.resources.getQuantityString(
                    R.plurals.lib_levels_installed,
                    cartes,
                    trouves.size,
                    cartes
                )
            )
        } catch (e: Throwable) {
            Issue.Echec(e.message ?: contexte.getString(R.string.lib_install_failed))
        } finally {
            temporaire.delete()
        }
    }
}
