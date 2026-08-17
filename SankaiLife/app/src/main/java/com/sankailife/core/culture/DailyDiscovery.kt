package com.sankailife.core.culture

import android.content.Context
import com.sankailife.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * La découverte du jour, partagée entre l'écran Capsules et la notification.
 *
 * Une seule source de vérité pour « quelle capsule aujourd'hui ? ». Sans ce
 * point commun, la notification annoncerait une capsule et l'écran en
 * ouvrirait une autre — exactement le défaut de cohérence que le système
 * d'historique existe pour éviter.
 */
object DailyDiscovery {

    /** Les entrées de tous les packs : embarqué puis locaux, préfixés. */
    data class Catalogue(
        val entries: List<DailyCultureEntry>,
        val version: String
    )

    fun catalogue(context: Context): Catalogue {
        val packStore = CulturePackStore(
            root = File(context.filesDir, "culture"),
            appVersionCode = BuildConfig.VERSION_CODE
        )
        val embarqué = runCatching { loadBundledPack(context) }.getOrNull()
            ?: return Catalogue(emptyList(), "vide")
        val liste = mutableListOf<Pair<DailyCultureEntry, String>>()
        liste += embarqué.entries.map { it to "${embarqué.manifest.id}@${embarqué.manifest.version}" }
        packStore.states().forEach { state ->
            if (state is CulturePackStore.State.Ready) {
                liste += state.pack.entries.map {
                    it.copy(id = "${state.pack.manifest.id}:${it.id}") to
                        "${state.pack.manifest.id}@${state.pack.manifest.version}"
                }
            }
        }
        return Catalogue(
            entries = liste.map { it.first },
            version = liste.joinToString(",") { it.second }
        )
    }

    /**
     * La capsule du jour, comme l'écran la montrerait.
     *
     * La sélection est déterministe (profil + date + version du pack) et
     * l'historique est partagé : la notification et l'écran tombent sur la
     * même capsule. Le choix du jour n'est pas enregistré ici — l'écran le
     * fait quand il est ouvert, et la notification ne doit pas avancer la
     * sélection sans qu'on l'ait vue.
     */
    fun duJour(
        context: Context,
        profileId: String = "user-1",
        history: List<CultureSelectionHistory> = emptyList()
    ): DailyCultureEntry? {
        val cat = catalogue(context)
        if (cat.entries.isEmpty()) return null
        return DailyCultureSelector.select(
            entries = cat.entries,
            request = DailyCultureSelectionRequest(
                profileId = profileId,
                localDate = LocalDate.now(),
                packVersion = cat.version,
                history = history,
                // La découverte suit le moment de la journée : mot le matin,
                // connaissance la journée, poésie le soir. Préférence douce.
                preferredTypes = MomentCulture.typesPreferees(
                    MomentCulture.moment(LocalTime.now().hour)
                )
            )
        )
    }

    /** Reconstruit en mémoire l'archive source livrée dans les assets. */
    private fun loadBundledPack(context: Context): ImportedCulturePack =
        ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                listAssetFiles(context).forEach { relativePath ->
                    zip.putNextEntry(ZipEntry(relativePath).apply { time = 0L })
                    context.assets.open("$ASSET_ROOT/$relativePath").use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            CulturePackImporter.inspect(bytes.toByteArray(), BuildConfig.VERSION_CODE)
        }

    private fun listAssetFiles(context: Context): List<String> {
        fun visit(path: String): List<String> {
            val children = context.assets.list(path).orEmpty().sorted()
            if (children.isEmpty()) return listOf(path.removePrefix("$ASSET_ROOT/"))
            return children.flatMap { child -> visit("$path/$child") }
        }
        return context.assets.list(ASSET_ROOT).orEmpty().sorted().flatMap { visit("$ASSET_ROOT/$it") }
    }

    private const val ASSET_ROOT = "culture/classics-fr-v1"
}
