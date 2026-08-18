package com.sankailife.core.poesie

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoesieDuJourSelectorTest {

    private val textes = listOf(
        PoesieDuJour(id = "a", type = TypeTexte.PROVERBE, texte = "Alpha."),
        PoesieDuJour(id = "b", type = TypeTexte.PROVERBE, texte = "Bêta.", drapeau = "🌍"),
        PoesieDuJour(id = "c", type = TypeTexte.POEME, texte = "Gamma.", auteur = "Un auteur"),
        PoesieDuJour(id = "d", type = TypeTexte.POEME, texte = "Delta.", oeuvre = "Une œuvre")
    )

    private val jour = LocalDate.of(2026, 8, 18)

    @Test
    fun `la meme date donne toujours la meme decouverte`() {
        val premier = PoesieDuJourSelector.selectionner(textes, jour)
        repeat(10) {
            assertEquals(premier, PoesieDuJourSelector.selectionner(textes, jour))
        }
    }

    @Test
    fun `le choix ne depend pas de l ordre du catalogue`() {
        val melange = textes.shuffled()
        assertEquals(
            PoesieDuJourSelector.selectionner(textes, jour),
            PoesieDuJourSelector.selectionner(melange, jour)
        )
    }

    @Test
    fun `la semaine fait tourner les decouvertes`() {
        val choisis = (0L..6L).map { decalage ->
            PoesieDuJourSelector.selectionner(textes, jour.plusDays(decalage))
        }.filterNotNull()
        assertEquals(7, choisis.size)
        assertTrue("La semaine doit varier : $choisis", choisis.distinct().size >= 2)
    }

    @Test
    fun `catalogue vide renvoie null`() {
        assertNull(PoesieDuJourSelector.selectionner(emptyList(), jour))
    }

    @Test
    fun `demain est le choix de la date suivante`() {
        assertEquals(
            PoesieDuJourSelector.selectionner(textes, jour.plusDays(1)),
            PoesieDuJourSelector.suivant(textes, jour)
        )
    }

    @Test
    fun `deux dates proches donnent souvent des textes differents`() {
        assertNotEquals(
            PoesieDuJourSelector.selectionner(textes, jour),
            PoesieDuJourSelector.suivant(textes, jour)
        )
    }

    @Test
    fun `parse distingue proverbes et poemes avec leurs champs`() {
        val catalogue = PoesieDuJourStore.parse(
            """
            [
              {
                "id": "p-1",
                "type": "proverbe",
                "texte": "Petit à petit, l'oiseau fait son nid.",
                "langue": "fr",
                "drapeau": "🇫🇷",
                "contexte": "Proverbe français."
              },
              {
                "id": "po-1",
                "type": "poeme",
                "texte": "Ô temps, suspends ton vol !",
                "auteur": "Alphonse de Lamartine",
                "oeuvre": "Le Lac",
                "annee": "1820",
                "contexte": "Domaine public."
              }
            ]
            """.trimIndent()
        )
        assertEquals(2, catalogue.size)
        val proverbe = catalogue.first()
        assertEquals(TypeTexte.PROVERBE, proverbe.type)
        assertEquals("Petit à petit, l'oiseau fait son nid.", proverbe.texte)
        assertEquals("fr", proverbe.langue)
        assertEquals("🇫🇷", proverbe.drapeau)
        assertNull(proverbe.auteur)
        val poeme = catalogue[1]
        assertEquals(TypeTexte.POEME, poeme.type)
        assertEquals("Alphonse de Lamartine", poeme.auteur)
        assertEquals("Le Lac", poeme.oeuvre)
        assertEquals("1820", poeme.annee)
        assertEquals("fr", poeme.langue)
    }

    @Test
    fun `le catalogue embarque est complet et coherent`() {
        val fichier = sequenceOf(
            File("src/main/assets/poesie_du_jour.json"),
            File("app/src/main/assets/poesie_du_jour.json")
        ).firstOrNull(File::isFile)
            ?: error("Le catalogue embarqué est introuvable depuis ${File(".").absolutePath}.")
        val catalogue = PoesieDuJourStore.parse(fichier.readText())
        assertTrue("Le catalogue doit contenir des découvertes", catalogue.isNotEmpty())
        // Chaque entrée a un texte non vide et un identifiant unique.
        val ids = catalogue.map { it.id }
        assertEquals("Les identifiants doivent être uniques", ids.size, ids.distinct().size)
        catalogue.forEach { entree ->
            assertTrue("Le texte ne doit pas être vide (${entree.id})", entree.texte.isNotBlank())
        }
        // Les deux types sont représentés.
        assertTrue(catalogue.any { it.type == TypeTexte.PROVERBE })
        assertTrue(catalogue.any { it.type == TypeTexte.POEME })
        // Toutes les œuvres citées sont du domaine public : les plus récentes
        // sont Pessoa (†1935) et Apollinaire (†1918).
        catalogue.filter { it.type == TypeTexte.POEME }.forEach { poeme ->
            assertTrue("Auteur renseigné pour un poème (${poeme.id})", !poeme.auteur.isNullOrBlank())
        }
    }
}
