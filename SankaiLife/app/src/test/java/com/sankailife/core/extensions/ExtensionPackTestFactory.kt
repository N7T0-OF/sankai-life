package com.sankailife.core.extensions

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ExtensionPackTestFactory {
    const val ID = SankaiExtensionContract.GARDEN_ID
    val defaultFiles = linkedMapOf(
        "data/world.json" to "{\"tiles\":[]}".toByteArray(),
        "README.md" to "# Jardin Sankai\nPack de donnees local.".toByteArray()
    )

    fun manifest(
        version: String = "1.0.0",
        minVersionCode: Int = 1,
        maxVersionCode: Int? = null,
        files: Map<String, ByteArray> = defaultFiles,
        dataOnly: Boolean = true,
        notificationsEnabled: Boolean = false
    ): ExtensionManifest {
        val assets = files.map { (path, bytes) ->
            ExtensionAsset(
                path = path,
                sizeBytes = bytes.size.toLong(),
                sha256 = ExtensionChecksums.sha256(bytes),
                mediaType = when (path.substringAfterLast('.', "")) {
                    "json" -> "application/json"
                    "md" -> "text/markdown"
                    "png" -> "image/png"
                    "dex" -> "application/octet-stream"
                    else -> "text/plain"
                }
            )
        }
        return ExtensionManifest(
            schemaVersion = ExtensionManifestCodec.CURRENT_SCHEMA_VERSION,
            id = ID,
            version = version,
            displayName = "Jardin Sankai",
            dataOnly = dataOnly,
            payloadSizeBytes = assets.sumOf { it.sizeBytes },
            compatibility = ExtensionCompatibility(minVersionCode, maxVersionCode),
            assets = assets,
            capabilities = setOf("garden.visual", "garden.ambient_audio"),
            entryScreen = ExtensionEntryScreen(SankaiExtensionContract.GARDEN_ENTRY_SCREEN_ID),
            settings = listOf(
                ExtensionSettingSpec(
                    id = "garden.animations",
                    type = ExtensionSettingType.BOOLEAN,
                    defaultValue = "true"
                )
            ),
            notifications = listOf(
                ExtensionNotificationSpec("garden.chest_ready", notificationsEnabled)
            ),
            saveSchema = ExtensionSaveSchema(
                ExtensionSaveKind.GARDEN_SUMMARY,
                GardenExtensionSnapshot.CURRENT_SCHEMA_VERSION
            ),
            payloadChecksumSha256 = ExtensionChecksums.payloadChecksum(assets)
        )
    }

    fun archive(
        manifest: ExtensionManifest,
        files: Map<String, ByteArray> = defaultFiles,
        extraEntries: List<Pair<String, ByteArray>> = emptyList()
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            add(zip, ExtensionManifestCodec.MANIFEST_FILE,
                ExtensionManifestCodec.encode(manifest).toByteArray())
            files.forEach { (path, bytes) -> add(zip, path, bytes) }
            extraEntries.forEach { (path, bytes) -> add(zip, path, bytes) }
        }
        return output.toByteArray()
    }

    fun writePack(
        directory: File,
        name: String,
        manifest: ExtensionManifest,
        files: Map<String, ByteArray> = defaultFiles,
        extraEntries: List<Pair<String, ByteArray>> = emptyList()
    ): File = File(directory, name).also {
        it.writeBytes(archive(manifest, files, extraEntries))
    }

    private fun add(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }
}
