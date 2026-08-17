package com.sankailife.core.extensions

import java.io.File

/** Identifiants declaratifs stables compris par l'hote, jamais interpretes comme du code. */
object SankaiExtensionContract {
    const val GARDEN_ID = "sankai.garden"
    const val GARDEN_ENTRY_SCREEN_ID = "garden.home"
}

/** Type de reglage compris et rendu par l'hote. Le paquet ne fournit aucun code. */
enum class ExtensionSettingType { BOOLEAN, INTEGER, ENUM, STRING }

/** Snapshots que le coeur sait relire sans charger de moteur d'extension. */
enum class ExtensionSaveKind { NONE, GARDEN_SUMMARY }

data class ExtensionCompatibility(
    val minAppVersionCode: Int,
    val maxAppVersionCode: Int?
)

/**
 * Un ecran deja enregistre dans Sankai Life.
 *
 * [hostScreenId] n'est ni un nom de classe, ni une route executable venant du
 * paquet. L'application decide seule quel ecran interne correspond a cet id.
 */
data class ExtensionEntryScreen(val hostScreenId: String)

data class ExtensionSettingSpec(
    val id: String,
    val type: ExtensionSettingType,
    val defaultValue: String?,
    val allowedValues: List<String> = emptyList()
)

/** Une categorie declarative. L'hote reste seul capable de programmer une alarme. */
data class ExtensionNotificationSpec(
    val id: String,
    val defaultEnabled: Boolean
)

data class ExtensionSaveSchema(
    val kind: ExtensionSaveKind,
    val version: Int
)

/** Un fichier passif lie au manifeste par sa taille et son SHA-256. */
data class ExtensionAsset(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val mediaType: String
)

/**
 * Contrat versionne d'une extension locale `.sankaipack`.
 *
 * Une extension est strictement DATA_ONLY. Elle peut apporter des donnees,
 * images, sons et cartes, mais jamais une classe, une bibliotheque native, un
 * script, un APK ou un autre executable. Desinstaller retire donc uniquement
 * donnees et assets ; le petit adaptateur hote reste dans l'application.
 */
data class ExtensionManifest(
    val schemaVersion: Int,
    val id: String,
    val version: String,
    val displayName: String,
    val dataOnly: Boolean,
    /** Taille decompressee de tous les fichiers hors extension.json. */
    val payloadSizeBytes: Long,
    val compatibility: ExtensionCompatibility,
    val assets: List<ExtensionAsset>,
    val capabilities: Set<String>,
    val entryScreen: ExtensionEntryScreen,
    val settings: List<ExtensionSettingSpec>,
    val notifications: List<ExtensionNotificationSpec>,
    val saveSchema: ExtensionSaveSchema,
    /** Empreinte canonique des chemins, tailles et SHA-256 declares. */
    val payloadChecksumSha256: String
)

/** Archive entierement controlee, encore inactive et sans aucun code chargeable. */
@ConsistentCopyVisibility
data class ValidatedExtensionPack internal constructor(
    val manifest: ExtensionManifest,
    val archiveChecksumSha256: String,
    internal val archiveFiles: Map<String, ByteArray>
)

/** Resume Garden conserve meme lorsque tous les assets ont ete retires. */
data class GardenExtensionSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val extensionId: String,
    val gardenLevel: Int,
    val discoveredPlantIds: Set<String>,
    val lastSavedAtEpochMillis: Long
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class InstalledExtension(
    val manifest: ExtensionManifest,
    val directory: File,
    val archiveChecksumSha256: String,
    /** Non nul lors d'une reinstallation du Jardin apres desinstallation. */
    val restoredGardenSnapshot: GardenExtensionSnapshot?
)

data class ExtensionUninstallResult(
    val deactivated: Boolean,
    val payloadDeleted: Boolean,
    val snapshotPreserved: Boolean
)
