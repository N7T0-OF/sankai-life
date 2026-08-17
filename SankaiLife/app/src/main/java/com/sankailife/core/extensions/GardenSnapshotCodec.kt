package com.sankailife.core.extensions

import java.io.IOException

/** Codec JVM du seul etat conserve apres retrait des assets Garden. */
internal object GardenSnapshotCodec {
    private val keys = setOf(
        "schemaVersion", "extensionId", "gardenLevel",
        "discoveredPlantIds", "lastSavedAtEpochMillis"
    )

    fun encode(snapshot: GardenExtensionSnapshot): String {
        validate(snapshot)
        return ExtensionJson.stringify(
            linkedMapOf(
                "schemaVersion" to snapshot.schemaVersion,
                "extensionId" to snapshot.extensionId,
                "gardenLevel" to snapshot.gardenLevel,
                "discoveredPlantIds" to snapshot.discoveredPlantIds.sorted(),
                "lastSavedAtEpochMillis" to snapshot.lastSavedAtEpochMillis
            )
        )
    }

    fun parse(text: String): GardenExtensionSnapshot {
        val json = ExtensionJson.parse(text).asExtensionJsonObject("snapshot Garden")
        json.requireOnlyKeys("snapshot Garden", keys)
        val plants = json.extensionStringList("discoveredPlantIds")
        if (plants.distinct().size != plants.size) {
            throw IOException("Le snapshot contient des plantes dupliquees.")
        }
        return GardenExtensionSnapshot(
            schemaVersion = json.extensionInt("schemaVersion"),
            extensionId = json.extensionString("extensionId"),
            gardenLevel = json.extensionInt("gardenLevel"),
            discoveredPlantIds = plants.toSet(),
            lastSavedAtEpochMillis = json.extensionLong("lastSavedAtEpochMillis")
        ).also(::validate)
    }

    fun validate(snapshot: GardenExtensionSnapshot) {
        if (snapshot.schemaVersion != GardenExtensionSnapshot.CURRENT_SCHEMA_VERSION) {
            throw IOException("Version de snapshot Garden non prise en charge.")
        }
        try {
            ExtensionManifestValidator.validateIdentifier(snapshot.extensionId, "extensionId du snapshot")
        } catch (error: ExtensionPackException) {
            throw IOException(error.message, error)
        }
        if (snapshot.gardenLevel !in 0..10_000) throw IOException("Niveau de Jardin invalide.")
        if (snapshot.extensionId != SankaiExtensionContract.GARDEN_ID) {
            throw IOException("Le snapshot n'appartient pas a l'extension Jardin Sankai.")
        }
        if (snapshot.discoveredPlantIds.size > MAX_DISCOVERED_PLANTS) {
            throw IOException("Snapshot Garden trop volumineux.")
        }
        snapshot.discoveredPlantIds.forEach { plantId ->
            try {
                ExtensionManifestValidator.validateIdentifier(plantId, "plante decouverte")
            } catch (error: ExtensionPackException) {
                throw IOException(error.message, error)
            }
        }
        if (snapshot.lastSavedAtEpochMillis <= 0L) {
            throw IOException("Date de sauvegarde Garden invalide.")
        }
    }

    private const val MAX_DISCOVERED_PLANTS = 2_048
}
