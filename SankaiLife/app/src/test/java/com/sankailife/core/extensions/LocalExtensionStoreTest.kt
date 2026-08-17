package com.sankailife.core.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalExtensionStoreTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `installation active une version complete sous le filesDir fourni`() {
        val manifest = ExtensionPackTestFactory.manifest()
        val packageFile = ExtensionPackTestFactory.writePack(
            temporary.root, "garden.sankaipack", manifest
        )
        val store = LocalExtensionStore(temporary.root, appVersionCode = 80)

        val installed = store.install(packageFile)

        assertEquals(manifest.id, installed.manifest.id)
        assertTrue(installed.directory.resolve("data/world.json").isFile)
        assertNull(installed.restoredGardenSnapshot)
        assertEquals(manifest.version, store.installed(manifest.id)?.manifest?.version)
    }

    @Test
    fun `desinstallation conserve le snapshot puis reinstallation le relit`() {
        val manifest = ExtensionPackTestFactory.manifest()
        val packageFile = ExtensionPackTestFactory.writePack(
            temporary.root, "garden.sankaipack", manifest
        )
        val store = LocalExtensionStore(temporary.root, appVersionCode = 80)
        store.install(packageFile)
        val snapshot = GardenExtensionSnapshot(
            extensionId = manifest.id,
            gardenLevel = 7,
            discoveredPlantIds = setOf("lavande", "tournesol", "cactus"),
            lastSavedAtEpochMillis = 1_786_000_000_000L
        )

        val removed = store.uninstall(manifest.id, gardenSnapshot = snapshot)

        assertTrue(removed.deactivated)
        assertTrue(removed.payloadDeleted)
        assertTrue(removed.snapshotPreserved)
        assertNull(store.installed(manifest.id))
        assertEquals(snapshot, store.loadGardenSnapshot(manifest.id))

        val reinstalled = store.install(packageFile)
        assertEquals(snapshot, reinstalled.restoredGardenSnapshot)
    }

    @Test
    fun `sans snapshot la desinstallation Garden est refusee par defaut`() {
        val manifest = ExtensionPackTestFactory.manifest()
        val packageFile = ExtensionPackTestFactory.writePack(
            temporary.root, "garden.sankaipack", manifest
        )
        val store = LocalExtensionStore(temporary.root, appVersionCode = 80)
        store.install(packageFile)

        assertStoreRejected { store.uninstall(manifest.id) }
        assertTrue(store.installed(manifest.id) != null)
    }

    @Test
    fun `un upgrade invalide laisse l'ancienne version active`() {
        val store = LocalExtensionStore(temporary.root, appVersionCode = 80)
        val versionOne = ExtensionPackTestFactory.manifest(version = "1.0.0")
        store.install(
            ExtensionPackTestFactory.writePack(
                temporary.root, "garden-v1.sankaipack", versionOne
            )
        )

        val versionTwo = ExtensionPackTestFactory.manifest(version = "2.0.0")
        val corruptedFiles = ExtensionPackTestFactory.defaultFiles.toMutableMap().apply {
            this["data/world.json"] = "{\"corrompu\":true}".toByteArray()
        }
        val invalid = ExtensionPackTestFactory.writePack(
            temporary.root, "garden-v2.sankaipack", versionTwo, corruptedFiles
        )

        try {
            store.install(invalid)
            fail("L'upgrade corrompu aurait du etre refuse.")
        } catch (_: ExtensionPackException) {
            // L'inspection se termine avant la moindre activation.
        }
        assertEquals("1.0.0", store.installed(versionOne.id)?.manifest?.version)
    }

    @Test
    fun `suppression explicite retire aussi le snapshot`() {
        val manifest = ExtensionPackTestFactory.manifest()
        val packageFile = ExtensionPackTestFactory.writePack(
            temporary.root, "garden.sankaipack", manifest
        )
        val store = LocalExtensionStore(temporary.root, appVersionCode = 80)
        store.install(packageFile)
        store.saveGardenSnapshot(
            GardenExtensionSnapshot(
                extensionId = manifest.id,
                gardenLevel = 2,
                discoveredPlantIds = setOf("ble"),
                lastSavedAtEpochMillis = 1_786_000_000_000L
            )
        )

        val removed = store.uninstall(manifest.id, preserveSnapshot = false)

        assertTrue(removed.deactivated)
        assertFalse(removed.snapshotPreserved)
        assertNull(store.loadGardenSnapshot(manifest.id))
    }

    @Test
    fun `snapshot Garden versionne est deterministe et refuse les doublons`() {
        val snapshot = GardenExtensionSnapshot(
            extensionId = ExtensionPackTestFactory.ID,
            gardenLevel = 9,
            discoveredPlantIds = setOf("rose", "ble"),
            lastSavedAtEpochMillis = 1_786_000_000_000L
        )
        val encoded = GardenSnapshotCodec.encode(snapshot)
        assertEquals(snapshot, GardenSnapshotCodec.parse(encoded))
        assertTrue(encoded.indexOf("ble") < encoded.indexOf("rose"))

        assertStoreRejected {
            GardenSnapshotCodec.parse(
                """{"schemaVersion":1,"extensionId":"sankai.garden","gardenLevel":1,"discoveredPlantIds":["rose","rose"],"lastSavedAtEpochMillis":1}"""
            )
        }
    }

    private fun assertStoreRejected(block: () -> Unit) {
        try {
            block()
            fail("L'operation aurait du etre refusee.")
        } catch (error: Exception) {
            assertTrue(error is ExtensionStoreException || error is java.io.IOException)
        }
    }
}
