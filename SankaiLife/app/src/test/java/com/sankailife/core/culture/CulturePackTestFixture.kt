package com.sankailife.core.culture

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object CulturePackTestFixture {
    const val DEFAULT_ENTRIES = """
        {
          "entries": [
            {
              "id": "hugo-demain-aube",
              "type": "POEM",
              "title": "Demain, dès l'aube",
              "body": "Demain, dès l'aube...",
              "author": "Victor Hugo",
              "authorBirthYear": 1802,
              "authorDeathYear": 1885,
              "workDate": "1847",
              "publicationDate": "1856",
              "countryCode": "FR",
              "languageCode": "fr",
              "context": "Poème des Contemplations.",
              "sourceLabel": "Wikisource",
              "sourceUrl": "https://fr.wikisource.org/",
              "rightsStatus": "PUBLIC_DOMAIN",
              "license": "Domaine public",
              "tags": ["poésie", "mémoire"],
              "difficulty": "LIGHT"
            }
          ]
        }
    """

    fun archive(
        id: String = "classics-fr",
        version: String = "1.0.0",
        minAppVersion: Int = 1,
        maxAppVersion: Int? = null,
        entriesJson: String = DEFAULT_ENTRIES,
        declaredEntryCount: Int = 1,
        declaredPayloadBytes: Long? = null,
        mutateHashes: Boolean = false,
        extraEntries: List<Pair<String, ByteArray>> = emptyList()
    ): ByteArray {
        val entries = entriesJson.trimIndent().toByteArray()
        val readme = "Pack de test".toByteArray()
        val license = "Domaine public".toByteArray()
        val payload = linkedMapOf(
            "entries.json" to entries,
            "README.md" to readme,
            "LICENSE" to license
        )
        extraEntries.forEach { (path, bytes) -> payload[path] = bytes }
        val filesJson = payload.entries.joinToString(",\n") { (path, bytes) ->
            val hash = if (mutateHashes && path == "entries.json") "0".repeat(64)
            else CulturePackImporter.digest(bytes)
            "\"$path\": \"$hash\""
        }
        val max = maxAppVersion?.let { ",\n  \"maxAppVersionCode\": $it" }.orEmpty()
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "$id",
              "version": "$version",
              "title": "Classiques français",
              "description": "Un pack local de test.",
              "languages": ["fr"],
              "license": "Domaine public",
              "sourceLabel": "Sources publiques",
              "minAppVersionCode": $minAppVersion$max,
              "entryCount": $declaredEntryCount,
              "payloadBytes": ${declaredPayloadBytes ?: payload.values.sumOf { it.size.toLong() }},
              "files": {
                $filesJson
              }
            }
        """.trimIndent().toByteArray()
        return zip(listOf("pack.json" to manifest) + payload.entries.map { it.key to it.value })
    }

    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
