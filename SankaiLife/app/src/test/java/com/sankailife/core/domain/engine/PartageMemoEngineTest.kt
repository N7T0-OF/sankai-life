package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartageMemoEngineTest {

    private val lignes = listOf(
        "Olá :: Bonjour",
        "Bom dia | Bonjour",
        "Obrigado — Merci",
        "Continue même si c'est lent"
    )

    @Test
    fun `les separateurs sont uniformises a l'export`() {
        // Les lignes en base utilisent plusieurs séparateurs selon la façon
        // dont elles ont été saisies. Ne pas les uniformiser produirait un
        // texte que l'import de Sankai Life relirait de travers.
        val texte = PartageMemoEngine.exporter("Portugais", lignes, avecEntete = false)
        assertTrue(texte.contains("Olá | Bonjour"))
        assertTrue(texte.contains("Obrigado | Merci"))
        assertTrue(!texte.contains("::"))
        assertTrue(!texte.contains("—"))
    }

    @Test
    fun `une ligne sans verso reste telle quelle`() {
        val texte = PartageMemoEngine.exporter("X", listOf("Respire"), avecEntete = false)
        assertEquals("Respire", texte)
    }

    @Test
    fun `l'entete annonce le theme et le compte`() {
        val texte = PartageMemoEngine.exporter("Portugais quotidien", lignes)
        assertTrue(texte.contains("Portugais quotidien"))
        assertTrue(texte.contains("4"))
    }

    @Test
    fun `l'export sans entete se recolle directement`() {
        // C'est le format d'échange : il doit contenir les lignes et rien
        // d'autre, sinon un collage réimporterait l'en-tête comme une carte.
        val texte = PartageMemoEngine.exporter("Portugais", lignes, avecEntete = false)
        assertEquals(4, texte.lines().size)
        assertTrue(!texte.contains("Thème"))
    }

    @Test
    fun `les lignes vides sont ecartees`() {
        val texte = PartageMemoEngine.exporter(
            "X", listOf("A | B", "", "   ", "C | D"), avecEntete = false
        )
        assertEquals(2, texte.lines().size)
    }

    @Test
    fun `un module sans nom garde un nom lisible`() {
        assertTrue(PartageMemoEngine.exporter("", lignes).contains("Mémo"))
        assertEquals("memo.txt", PartageMemoEngine.nomFichier(""))
    }

    @Test
    fun `le nom de fichier est utilisable sur un systeme de fichiers`() {
        assertEquals("portugais-quotidien.txt", PartageMemoEngine.nomFichier("Portugais quotidien"))
        assertEquals("ete-2026.txt", PartageMemoEngine.nomFichier("Été 2026 !"))
        // Un nom entierement compose de caracteres ecartes ne doit pas donner
        // un fichier sans nom.
        assertEquals("memo.txt", PartageMemoEngine.nomFichier("!!!"))
    }
}
