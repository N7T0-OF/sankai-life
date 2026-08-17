package com.sankailife.core.culture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CulturePackImporterTest {

    @Test
    fun `un paquet complet et signe est accepte`() {
        val pack = CulturePackImporter.inspect(CulturePackTestFixture.archive(), appVersionCode = 80)

        assertEquals("classics-fr", pack.manifest.id)
        assertEquals(1, pack.entries.size)
        assertEquals(ContentRightsStatus.PUBLIC_DOMAIN, pack.entries.single().rightsStatus)
        assertEquals(setOf("entries.json", "README.md", "LICENSE"), pack.files.keys)
    }

    @Test
    fun `une empreinte incorrecte est refusee`() {
        assertRefused("Empreinte incorrecte") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(mutateHashes = true),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `un executable est refuse meme non declare`() {
        val bytes = CulturePackTestFixture.zip(
            listOf(
                "pack.json" to byteArrayOf(),
                "entries.json" to byteArrayOf(),
                "README.md" to byteArrayOf(),
                "LICENSE" to byteArrayOf(),
                "media/payload.dex" to byteArrayOf(1, 2, 3)
            )
        )
        assertRefused("Type de fichier interdit") {
            CulturePackImporter.inspect(bytes, appVersionCode = 80)
        }
    }

    @Test
    fun `une traversee de chemin est refusee`() {
        val bytes = CulturePackTestFixture.zip(
            listOf("media/../../databases/private.png" to byteArrayOf(1))
        )
        assertRefused("Chemin interdit") {
            CulturePackImporter.inspect(bytes, appVersionCode = 80)
        }
    }

    @Test
    fun `deux chemins qui ne different que par la casse sont refuses`() {
        val bytes = CulturePackTestFixture.zip(
            listOf(
                "media/image.png" to byteArrayOf(1),
                "media/IMAGE.png" to byteArrayOf(2)
            )
        )
        assertRefused("dupliquée") {
            CulturePackImporter.inspect(bytes, appVersionCode = 80)
        }
    }

    @Test
    fun `une version incompatible est refusee`() {
        assertRefused("pas compatible") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(minAppVersion = 81),
                appVersionCode = 80
            )
        }
        assertRefused("pas compatible") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(maxAppVersion = 79),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `le nombre annonce doit correspondre au catalogue`() {
        assertRefused("annonce 2") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(declaredEntryCount = 2),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `la taille decompressee annoncee doit correspondre`() {
        assertRefused("Taille décompressée incorrecte") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(declaredPayloadBytes = 42),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `un media passif declare et signe est accepte`() {
        val entries = CulturePackTestFixture.DEFAULT_ENTRIES.replace(
            "\"difficulty\": \"LIGHT\"",
            "\"difficulty\": \"LIGHT\", \"mediaPath\": \"media/cover.webp\""
        )
        val pack = CulturePackImporter.inspect(
            CulturePackTestFixture.archive(
                entriesJson = entries,
                extraEntries = listOf("media/cover.webp" to byteArrayOf(1, 2, 3))
            ),
            appVersionCode = 80
        )

        assertTrue(pack.files.containsKey("media/cover.webp"))
        assertEquals("media/cover.webp", pack.entries.single().mediaPath)
    }

    @Test
    fun `un contenu metadata only ne transporte pas le texte protege`() {
        val entries = CulturePackTestFixture.DEFAULT_ENTRIES
            .replace("PUBLIC_DOMAIN", "METADATA_ONLY")
        assertRefused("limité aux métadonnées") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(entriesJson = entries),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `les identifiants de contenu sont uniques`() {
        val one = CulturePackTestFixture.DEFAULT_ENTRIES.trimIndent()
        val entryObject = one.substringAfter("[").substringBeforeLast("]").trim()
        val duplicate = "{\"entries\": [$entryObject, $entryObject]}"
        assertRefused("dupliqué") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(entriesJson = duplicate, declaredEntryCount = 2),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `un media reference doit exister dans le paquet`() {
        val entries = CulturePackTestFixture.DEFAULT_ENTRIES.replace(
            "\"difficulty\": \"LIGHT\"",
            "\"difficulty\": \"LIGHT\", \"mediaPath\": \"media/absent.png\""
        )
        assertRefused("Média absent") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(entriesJson = entries),
                appVersionCode = 80
            )
        }
    }

    @Test
    fun `un json a cle dupliquee est refuse`() {
        val entries = CulturePackTestFixture.DEFAULT_ENTRIES.replace(
            "\"title\": \"Demain, dès l'aube\"",
            "\"title\": \"A\", \"title\": \"B\""
        )
        assertRefused("dupliquée") {
            CulturePackImporter.inspect(
                CulturePackTestFixture.archive(entriesJson = entries),
                appVersionCode = 80
            )
        }
    }

    private fun assertRefused(expected: String, block: () -> Unit) {
        try {
            block()
            fail("Le paquet aurait dû être refusé.")
        } catch (error: Exception) {
            assertTrue(
                "Message inattendu : ${error.message}",
                error.message.orEmpty().contains(expected, ignoreCase = true)
            )
        }
    }
}
