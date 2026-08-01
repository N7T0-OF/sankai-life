package com.sankailife.core.data.sauvegarde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SauvegardeEngineTest {

    private fun manifeste(
        versionFormat: Int = SauvegardeEngine.VERSION_FORMAT,
        versionApp: Int = 30,
        sections: List<String> = listOf("profile", "memos"),
        empreinte: String = "abc"
    ) = SauvegardeEngine.Manifeste(versionFormat, versionApp, "2026-08-01", sections, empreinte)

    @Test
    fun `un fichier sans manifeste est refuse`() {
        val v = SauvegardeEngine.verifier(null, "abc", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Refuse)
    }

    @Test
    fun `un format plus recent est refuse avec une raison utile`() {
        val v = SauvegardeEngine.verifier(manifeste(versionFormat = 99), "abc", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Refuse)
        assertTrue((v as SauvegardeEngine.Verdict.Refuse).raison.contains("Mets à jour"))
    }

    @Test
    fun `une empreinte qui ne correspond pas est refusee`() {
        val v = SauvegardeEngine.verifier(manifeste(empreinte = "abc"), "different", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Refuse)
        assertTrue((v as SauvegardeEngine.Verdict.Refuse).raison.contains("abîmé"))
    }

    @Test
    fun `une sauvegarde vide est refusee`() {
        val v = SauvegardeEngine.verifier(manifeste(sections = emptyList()), "abc", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Refuse)
    }

    @Test
    fun `une sauvegarde saine est acceptee sans reserve`() {
        val v = SauvegardeEngine.verifier(manifeste(), "abc", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Utilisable)
        assertNull((v as SauvegardeEngine.Verdict.Utilisable).reserve)
    }

    @Test
    fun `une version d'app plus recente passe, mais avec une reserve`() {
        // Bloquer sur ce seul critère priverait quelqu'un de ses données pour
        // une raison qui n'en est pas une : le format, lui, est compatible.
        val v = SauvegardeEngine.verifier(manifeste(versionApp = 99), "abc", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Utilisable)
        assertNotNull((v as SauvegardeEngine.Verdict.Utilisable).reserve)
    }

    @Test
    fun `une empreinte absente ne bloque pas`() {
        // Une sauvegarde ancienne peut ne pas en avoir. Refuser reviendrait à
        // rendre illisibles des fichiers parfaitement sains.
        val v = SauvegardeEngine.verifier(manifeste(empreinte = ""), "peu importe", 30)
        assertTrue(v is SauvegardeEngine.Verdict.Utilisable)
    }

    @Test
    fun `on ne restaure que ce qui est present et demande`() {
        val sections = SauvegardeEngine.sectionsARestaurer(
            presentes = listOf("profile", "memos"),
            demandees = setOf(
                SauvegardeEngine.Section.PROFIL,
                SauvegardeEngine.Section.JARDIN
            )
        )
        assertEquals(listOf(SauvegardeEngine.Section.PROFIL), sections)
    }

    @Test
    fun `les chemins dangereux sont ecartes`() {
        // Une archive est un fichier reçu de l'extérieur. Une entrée nommée
        // « ../../databases/sankai_db » écrirait hors du dossier prévu.
        assertTrue(SauvegardeEngine.cheminSur("profile.json"))
        assertTrue(SauvegardeEngine.cheminSur("media/photo.png"))
        assertFalse(SauvegardeEngine.cheminSur("../evasion.json"))
        assertFalse(SauvegardeEngine.cheminSur("/etc/passwd"))
        assertFalse(SauvegardeEngine.cheminSur("dossier\\fichier.json"))
        assertFalse(SauvegardeEngine.cheminSur("C:/windows"))
        assertFalse(SauvegardeEngine.cheminSur(""))
    }

    @Test
    fun `l'empreinte change des que le contenu change`() {
        val a = SauvegardeEngine.empreinte("bonjour".toByteArray())
        val b = SauvegardeEngine.empreinte("bonjour.".toByteArray())
        assertEquals(64, a.length)
        assertTrue(a != b)
        assertEquals(a, SauvegardeEngine.empreinte("bonjour".toByteArray()))
    }

    @Test
    fun `le nom de fichier se trie chronologiquement`() {
        // La date est en tête pour que le tri alphabétique du sélecteur de
        // fichiers donne aussi l'ordre chronologique.
        val noms = listOf("2026-08-01", "2026-01-15", "2026-12-31")
            .map { SauvegardeEngine.nomFichier(it) }
        assertEquals(noms.sorted(), noms.sortedBy { it })
        assertTrue(noms.all { it.endsWith(".sankai") })
    }
}
