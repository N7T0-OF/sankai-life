package com.sankailife.core.data.archive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BoundedZipReaderTest {

    @Test
    fun `une petite archive ne conserve que les entrees acceptees`() {
        val archive = archive(
            "module.json" to "{}".toByteArray(),
            "cartes.txt" to "bonjour".toByteArray(),
            "../hors-zone" to "secret".toByteArray()
        )

        val contenu = lire(
            archive = archive,
            limits = BoundedZipReader.Limits(
                maxTotalBytes = 64,
                maxEntryBytes = 32,
                maxEntries = 4
            ),
            accepter = { !it.startsWith("../") }
        )

        assertEquals(setOf("module.json", "cartes.txt"), contenu.keys)
        assertArrayEquals("bonjour".toByteArray(), contenu.getValue("cartes.txt"))
        assertFalse(contenu.containsKey("../hors-zone"))
    }

    @Test
    fun `la limite par entree est appliquee pendant la decompression`() {
        val archive = archive("bombe.txt" to ByteArray(4_096))

        assertLimiteDepassee {
            lire(
                archive,
                BoundedZipReader.Limits(
                    maxTotalBytes = 8_192,
                    maxEntryBytes = 1_024,
                    maxEntries = 2
                )
            )
        }
    }

    @Test
    fun `les entrees refusees comptent dans la limite globale`() {
        val archive = archive(
            "garde.txt" to ByteArray(6),
            "../ignore.txt" to ByteArray(6)
        )

        assertLimiteDepassee {
            lire(
                archive = archive,
                limits = BoundedZipReader.Limits(
                    maxTotalBytes = 10,
                    maxEntryBytes = 8,
                    maxEntries = 3
                ),
                accepter = { !it.startsWith("../") }
            )
        }
    }

    @Test
    fun `toutes les entrees comptent dans la limite de complexite`() {
        val archive = archive(
            "un.txt" to ByteArray(0),
            "../deux.txt" to ByteArray(0),
            "trois.txt" to ByteArray(0)
        )

        assertLimiteDepassee {
            lire(
                archive = archive,
                limits = BoundedZipReader.Limits(
                    maxTotalBytes = 16,
                    maxEntryBytes = 8,
                    maxEntries = 2
                ),
                accepter = { !it.startsWith("../") }
            )
        }
    }

    private fun lire(
        archive: ByteArray,
        limits: BoundedZipReader.Limits,
        accepter: (String) -> Boolean = { true }
    ): Map<String, ByteArray> = ByteArrayInputStream(archive).use { source ->
        BoundedZipReader.read(source, limits, accepter)
    }

    private fun archive(vararg entrees: Pair<String, ByteArray>): ByteArray {
        val sortie = ByteArrayOutputStream()
        ZipOutputStream(sortie).use { zip ->
            entrees.forEach { (nom, contenu) ->
                zip.putNextEntry(ZipEntry(nom))
                zip.write(contenu)
                zip.closeEntry()
            }
        }
        return sortie.toByteArray()
    }

    private fun assertLimiteDepassee(action: () -> Unit) {
        try {
            action()
            fail("L'archive aurait du etre refusee.")
        } catch (_: BoundedZipReader.LimitExceededException) {
            // Verdict attendu.
        }
    }
}
