package com.sankailife.core.culture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CulturePackExampleAssetTest {

    @Test
    fun `le pack exemple est importable et ses sources sont presentes`() {
        val directory = sequenceOf(
            File("src/main/assets/culture/classics-fr-v1"),
            File("app/src/main/assets/culture/classics-fr-v1")
        ).firstOrNull(File::isDirectory)
            ?: error("Le pack culturel d'exemple est introuvable depuis ${File(".").absolutePath}.")

        val required = listOf("pack.json", "entries.json", "README.md", "LICENSE")
        assertTrue(required.all { directory.resolve(it).isFile })
        val archive = CulturePackTestFixture.zip(
            required.map { name -> name to directory.resolve(name).readBytes() }
        )

        val pack = CulturePackImporter.inspect(archive, appVersionCode = 80)
        assertEquals("classics-fr-sample", pack.manifest.id)
        assertEquals(3, pack.entries.size)
        assertTrue(pack.entries.all { it.rightsStatus == ContentRightsStatus.PUBLIC_DOMAIN })
        assertTrue(pack.entries.all { !it.sourceLabel.isNullOrBlank() })
    }
}

