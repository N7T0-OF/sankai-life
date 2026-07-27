package com.sankailife.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.sankailife.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Recherche et installation des mises à jour depuis les Releases GitHub.
 *
 * Le dépôt est public : aucun jeton n'est nécessaire, et surtout aucun n'est
 * embarqué dans l'APK — un jeton dans une application distribuée est
 * extractible par n'importe qui.
 *
 * Limite Android assumée : une application ordinaire **ne peut pas** installer
 * un APK silencieusement. On télécharge, on vérifie, puis on ouvre
 * l'installateur système ; l'utilisateur confirme toujours.
 */
object UpdateManager {

    private const val DEPOT = "N7T0-OF/sankai-life"
    private const val URL_DERNIERE = "https://api.github.com/repos/$DEPOT/releases/latest"

    /**
     * Hôtes acceptés pour un téléchargement. GitHub redirige les assets vers
     * objects.githubusercontent.com : sans cette liste, une Release piégée
     * pourrait faire télécharger n'importe quoi depuis n'importe où.
     */
    private val HOTES_AUTORISES = setOf(
        "api.github.com", "github.com", "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    /** Un APK de plus de 100 Mo n'est pas cette application. */
    private const val TAILLE_MAX_OCTETS = 100L * 1024 * 1024

    private const val DELAI_MS = 20_000

    data class Maj(
        val versionName: String,
        val versionCode: Int,
        val urlApk: String,
        val sha256: String,
        val tailleOctets: Long,
        val nouveautes: List<String>
    )

    sealed interface Resultat {
        data class AJour(val versionActuelle: String) : Resultat
        data class Disponible(val maj: Maj) : Resultat
        data class Erreur(val message: String) : Resultat
    }

    /** Version installée, telle qu'affichée dans les paramètres. */
    val versionInstallee: String get() = BuildConfig.VERSION_NAME
    val codeInstalle: Int get() = BuildConfig.VERSION_CODE

    // -----------------------------------------------------------------------
    // Recherche
    // -----------------------------------------------------------------------

    suspend fun rechercher(): Resultat = withContext(Dispatchers.IO) {
        runCatching {
            val release = JSONObject(lireTexte(URL_DERNIERE))
            val assets = release.getJSONArray("assets")

            var urlMetadonnees: String? = null
            var urlApk: String? = null
            var tailleApk = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val nom = asset.getString("name")
                val url = asset.getString("browser_download_url")
                when {
                    nom == "update.json" -> urlMetadonnees = url
                    nom.endsWith(".apk") -> {
                        urlApk = url
                        tailleApk = asset.optLong("size", 0L)
                    }
                }
            }

            if (urlApk == null) {
                return@runCatching Resultat.Erreur("Cette version ne contient pas d'APK")
            }

            // update.json porte le versionCode et l'empreinte. Sans lui on ne
            // peut ni comparer de façon fiable ni vérifier le fichier.
            if (urlMetadonnees == null) {
                return@runCatching Resultat.Erreur("Informations de version indisponibles")
            }

            val meta = JSONObject(lireTexte(urlMetadonnees))
            val codeDistant = meta.getInt("versionCode")
            val nomDistant = meta.optString("versionName", release.optString("tag_name"))
            val sha = meta.optString("sha256", "")

            val notes = mutableListOf<String>()
            meta.optJSONArray("changelog")?.let { tableau ->
                for (i in 0 until tableau.length()) notes.add(tableau.getString(i))
            }

            // Refus explicite d'une version antérieure ou identique : une
            // Release mal taguée ne doit jamais provoquer de rétrogradation.
            if (codeDistant <= codeInstalle) {
                Resultat.AJour(versionInstallee)
            } else {
                Resultat.Disponible(
                    Maj(nomDistant, codeDistant, urlApk, sha, tailleApk, notes)
                )
            }
        }.getOrElse { e ->
            Resultat.Erreur(messageLisible(e))
        }
    }

    // -----------------------------------------------------------------------
    // Téléchargement + installation
    // -----------------------------------------------------------------------

    /**
     * Télécharge l'APK, vérifie son empreinte, puis ouvre l'installateur.
     * @param onProgression appelée avec une valeur entre 0 et 1.
     * @return null si tout s'est bien passé, sinon le message d'erreur.
     */
    suspend fun telechargerEtInstaller(
        context: Context,
        maj: Maj,
        onProgression: (Float) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            verifierHote(maj.urlApk)?.let { return@runCatching it }

            val dossier = File(context.cacheDir, "updates").apply { mkdirs() }
            // Un seul fichier de mise à jour à la fois : on nettoie l'ancien.
            dossier.listFiles()?.forEach { it.delete() }
            val fichier = File(dossier, "SankaiLife-${maj.versionName}.apk")

            val connexion = ouvrir(maj.urlApk)
            val total = if (maj.tailleOctets > 0) maj.tailleOctets
                        else connexion.contentLengthLong.coerceAtLeast(1L)
            if (total > TAILLE_MAX_OCTETS) {
                return@runCatching "Fichier anormalement volumineux, téléchargement annulé"
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var recu = 0L

            connexion.inputStream.use { entree ->
                fichier.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    while (true) {
                        val lus = entree.read(tampon)
                        if (lus <= 0) break
                        sortie.write(tampon, 0, lus)
                        digest.update(tampon, 0, lus)
                        recu += lus
                        if (recu > TAILLE_MAX_OCTETS) {
                            return@runCatching "Fichier trop volumineux, téléchargement interrompu"
                        }
                        onProgression((recu.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }

            // Vérification d'intégrité : protège d'un téléchargement corrompu
            // comme d'un fichier substitué en chemin.
            if (maj.sha256.isNotBlank()) {
                val calcule = digest.digest().joinToString("") { "%02x".format(it) }
                if (!calcule.equals(maj.sha256.trim(), ignoreCase = true)) {
                    fichier.delete()
                    return@runCatching "Empreinte du fichier incorrecte, installation annulée"
                }
            }

            // Dernier garde-fou : le paquet doit bien être Sankai Life.
            val info = context.packageManager.getPackageArchiveInfo(fichier.absolutePath, 0)
            val attendu = context.packageName.removeSuffix(".debug")
            if (info == null || info.packageName.removeSuffix(".debug") != attendu) {
                fichier.delete()
                return@runCatching "Le fichier téléchargé n'est pas Sankai Life"
            }

            lancerInstallation(context, fichier)
            null
        }.getOrElse { e -> messageLisible(e) }
    }

    /** true si Android autorise l'app à demander une installation. */
    fun peutInstaller(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** Écran système « autoriser cette source ». */
    fun intentAutorisationInstallation(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else null

    private fun lancerInstallation(context: Context, fichier: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", fichier
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // -----------------------------------------------------------------------
    // Utilitaires réseau
    // -----------------------------------------------------------------------

    private fun verifierHote(url: String): String? {
        val hote = runCatching { URL(url).host }.getOrNull()
        return if (hote != null && HOTES_AUTORISES.any { hote == it || hote.endsWith(".$it") }) null
               else "Source de téléchargement non autorisée"
    }

    private fun ouvrir(url: String): HttpURLConnection {
        val connexion = URL(url).openConnection() as HttpURLConnection
        connexion.connectTimeout = DELAI_MS
        connexion.readTimeout = DELAI_MS
        connexion.instanceFollowRedirects = true
        connexion.setRequestProperty("Accept", "application/octet-stream")
        connexion.setRequestProperty("User-Agent", "SankaiLife/${BuildConfig.VERSION_NAME}")
        return connexion
    }

    private fun lireTexte(url: String): String {
        verifierHote(url)?.let { throw IllegalArgumentException(it) }
        val connexion = ouvrir(url)
        connexion.setRequestProperty("Accept", "application/vnd.github+json")
        return connexion.inputStream.bufferedReader().use { it.readText() }
    }

    private fun messageLisible(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Pas de connexion internet"
        is java.net.SocketTimeoutException -> "Le serveur ne répond pas"
        is java.io.FileNotFoundException -> "Aucune version publiée trouvée"
        else -> e.message ?: "Erreur inconnue"
    }
}
