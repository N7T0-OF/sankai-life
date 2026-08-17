package com.sankailife.core.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExtensionPackInspectorTest {

    @Test
    fun `un paquet passif compatible est valide avec ses deux checksums`() {
        val manifest = ExtensionPackTestFactory.manifest(minVersionCode = 70, maxVersionCode = 90)
        val bytes = ExtensionPackTestFactory.archive(manifest)

        val pack = ExtensionPackInspector().inspect(bytes, "sankai-garden.sankaipack", 80)

        assertEquals(ExtensionPackTestFactory.ID, pack.manifest.id)
        assertEquals(64, pack.archiveChecksumSha256.length)
        assertEquals(ExtensionChecksums.sha256(bytes), pack.archiveChecksumSha256)
        assertTrue(pack.manifest.dataOnly)
        assertEquals(manifest.payloadChecksumSha256, pack.manifest.payloadChecksumSha256)
    }

    @Test
    fun `la compatibilite versionCode est obligatoire`() {
        val manifest = ExtensionPackTestFactory.manifest(minVersionCode = 81)
        assertPackRejected {
            ExtensionPackInspector().inspect(
                ExtensionPackTestFactory.archive(manifest), "garden.sankaipack", 80
            )
        }
    }

    @Test
    fun `zip slip et chemins Windows absolus sont refuses`() {
        val manifest = ExtensionPackTestFactory.manifest()
        listOf("../sortie.json", "C:/sortie.json", "data\\sortie.json").forEach { path ->
            assertPackRejected {
                ExtensionPackInspector().inspect(
                    ExtensionPackTestFactory.archive(
                        manifest, extraEntries = listOf(path to "{}".toByteArray())
                    ),
                    "garden.sankaipack", 80
                )
            }
        }
    }

    @Test
    fun `deux chemins differant seulement par la casse sont refuses`() {
        val manifest = ExtensionPackTestFactory.manifest()
        assertPackRejected {
            ExtensionPackInspector().inspect(
                ExtensionPackTestFactory.archive(
                    manifest,
                    extraEntries = listOf("data/World.json" to "{}".toByteArray())
                ),
                "garden.sankaipack", 80
            )
        }
    }

    @Test
    fun `classes scripts binaires et archives actives restent interdits`() {
        listOf("dex", "class", "jar", "so", "apk", "js", "wasm", "sh", "svg", "html")
            .forEach { extension ->
                val files = mapOf("data/engine.$extension" to byteArrayOf(1, 2, 3))
                val manifest = ExtensionPackTestFactory.manifest(files = files)
                assertPackRejected {
                    ExtensionPackInspector().inspect(
                        ExtensionPackTestFactory.archive(manifest, files),
                        "garden.sankaipack", 80
                    )
                }
            }
    }

    @Test
    fun `un dex renomme en image ne devient pas un asset passif`() {
        val files = mapOf("assets/engine.png" to "dex\n035\u0000".toByteArray())
        val manifest = ExtensionPackTestFactory.manifest(files = files)
        assertPackRejected {
            ExtensionPackInspector().inspect(
                ExtensionPackTestFactory.archive(manifest, files), "garden.sankaipack", 80
            )
        }
    }

    @Test
    fun `les limites de decompression bloquent une bombe avant conservation`() {
        val large = ByteArray(4_097)
        val files = mapOf("data/world.json" to large)
        val manifest = ExtensionPackTestFactory.manifest(files = files)
        val inspector = ExtensionPackInspector(
            ExtensionPackLimits(
                maxArchiveBytes = 16_384,
                maxTotalBytes = 8_192,
                maxEntryBytes = 4_096,
                maxEntries = 8,
                maxManifestBytes = 4_096
            )
        )
        assertPackRejected {
            inspector.inspect(
                ExtensionPackTestFactory.archive(manifest, files), "garden.sankaipack", 80
            )
        }
    }

    @Test
    fun `une empreinte par fichier incorrecte est refusee`() {
        val files = ExtensionPackTestFactory.defaultFiles
        val original = ExtensionPackTestFactory.manifest(files = files)
        val brokenAsset = original.assets.first().copy(sha256 = "0".repeat(64))
        val assets = listOf(brokenAsset) + original.assets.drop(1)
        val manifest = original.copy(
            assets = assets,
            payloadChecksumSha256 = ExtensionChecksums.payloadChecksum(assets)
        )
        assertPackRejected {
            ExtensionPackInspector().inspect(
                ExtensionPackTestFactory.archive(manifest, files), "garden.sankaipack", 80
            )
        }
    }

    @Test
    fun `un checksum global incorrect est refuse`() {
        val manifest = ExtensionPackTestFactory.manifest().copy(
            payloadChecksumSha256 = "0".repeat(64)
        )
        assertPackRejected {
            ExtensionPackInspector().inspect(
                ExtensionPackTestFactory.archive(manifest), "garden.sankaipack", 80
            )
        }
    }

    @Test
    fun `notifications actives et dataOnly faux sont refuses`() {
        val activeNotification = ExtensionPackTestFactory.manifest(notificationsEnabled = true)
        val executableMode = ExtensionPackTestFactory.manifest(dataOnly = false)
        listOf(activeNotification, executableMode).forEach { manifest ->
            assertPackRejected {
                ExtensionPackInspector().inspect(
                    ExtensionPackTestFactory.archive(manifest), "garden.sankaipack", 80
                )
            }
        }
    }

    @Test
    fun `un zip ordinaire n'est pas presente comme extension`() {
        val manifest = ExtensionPackTestFactory.manifest()
        assertPackRejected {
            ExtensionPackInspector().inspect(
                ExtensionPackTestFactory.archive(manifest), "garden.zip", 80
            )
        }
    }

    private fun assertPackRejected(block: () -> Unit) {
        try {
            block()
            fail("Le paquet dangereux aurait du etre refuse.")
        } catch (error: ExtensionPackException) {
            assertFalse(error.message.isNullOrBlank())
        }
    }
}
