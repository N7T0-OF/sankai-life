package com.sankailife.core.culture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CulturePackStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `installer charger puis desinstaller un paquet local`() {
        val store = CulturePackStore(temporary.newFolder("culture"), appVersionCode = 80)
        val installed = CulturePackTestFixture.archive().inputStream().use(store::install)

        assertEquals("classics-fr", installed.manifest.id)
        assertEquals("1.0.0", store.load("classics-fr")?.manifest?.version)
        assertEquals(1, store.states().size)
        assertTrue(store.uninstall("classics-fr"))
        assertNull(store.load("classics-fr"))
        assertFalse(store.uninstall("classics-fr"))
    }

    @Test
    fun `une mise a jour invalide preserve la version installee`() {
        val store = CulturePackStore(temporary.newFolder("culture"), appVersionCode = 80)
        CulturePackTestFixture.archive(version = "1.0.0").inputStream().use(store::install)

        try {
            CulturePackTestFixture.archive(version = "2.0.0", mutateHashes = true)
                .inputStream()
                .use(store::install)
            fail("La mise à jour aurait dû être refusée.")
        } catch (_: CulturePackException) {
            // Le paquet existant n'a pas encore été touché.
        }

        assertEquals("1.0.0", store.load("classics-fr")?.manifest?.version)
    }

    @Test
    fun `une mise a jour valide remplace atomiquement le meme identifiant`() {
        val store = CulturePackStore(temporary.newFolder("culture"), appVersionCode = 80)
        CulturePackTestFixture.archive(version = "1.0.0").inputStream().use(store::install)
        CulturePackTestFixture.archive(version = "2.0.0").inputStream().use(store::install)

        assertEquals("2.0.0", store.load("classics-fr")?.manifest?.version)
        assertEquals(1, store.states().size)
    }

    @Test
    fun `un paquet corrompu reste visible dans le diagnostic`() {
        val root = temporary.newFolder("culture")
        root.resolve("broken.culturepack").writeText("pas un zip")
        val states = CulturePackStore(root, appVersionCode = 80).states()

        assertEquals(1, states.size)
        assertTrue(states.single() is CulturePackStore.State.Invalid)
    }
}

