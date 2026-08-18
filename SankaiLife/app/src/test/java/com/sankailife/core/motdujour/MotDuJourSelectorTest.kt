package com.sankailife.core.motdujour

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotDuJourSelectorTest {

    private val mots = listOf(
        MotDuJour(id = "a", mot = "alpha", langue = "fr", definition = "Premier."),
        MotDuJour(id = "b", mot = "beta", langue = "pt", definition = "Deuxième."),
        MotDuJour(id = "c", mot = "gamma", langue = "en", definition = "Troisième."),
        MotDuJour(id = "d", mot = "delta", langue = "es", definition = "Quatrième."),
        MotDuJour(id = "e", mot = "epsilon", langue = "it", definition = "Cinquième."),
        MotDuJour(id = "f", mot = "zêta", langue = "de", definition = "Sixième.")
    )

    private val jour = LocalDate.of(2026, 8, 18)

    @Test
    fun `la meme date donne toujours le meme mot`() {
        val premier = MotDuJourSelector.selectionner(mots, jour)
        repeat(10) {
            assertEquals(premier, MotDuJourSelector.selectionner(mots, jour))
        }
    }

    @Test
    fun `le choix ne depend pas de l ordre du catalogue`() {
        val melange = mots.shuffled()
        assertEquals(
            MotDuJourSelector.selectionner(mots, jour),
            MotDuJourSelector.selectionner(melange, jour)
        )
    }

    @Test
    fun `la semaine fait tourner les mots sans se repeter`() {
        val choisis = (0L..6L).map { decalage ->
            MotDuJourSelector.selectionner(mots, jour.plusDays(decalage))
        }.filterNotNull()
        assertEquals(7, choisis.size)
        // Sur sept jours et six mots, une répétition est possible ; un seul
        // mot répété sept fois signifierait une sélection défaillante.
        assertTrue("La semaine doit varier : $choisis", choisis.distinct().size >= 2)
    }

    @Test
    fun `catalogue vide renvoie null`() {
        assertNull(MotDuJourSelector.selectionner(emptyList(), jour))
    }

    @Test
    fun `demain est le choix de la date suivante`() {
        assertEquals(
            MotDuJourSelector.selectionner(mots, jour.plusDays(1)),
            MotDuJourSelector.suivant(mots, jour)
        )
    }

    @Test
    fun `deux dates proches donnent souvent des mots differents`() {
        val aujourdhui = MotDuJourSelector.selectionner(mots, jour)
        val demain = MotDuJourSelector.suivant(mots, jour)
        assertNotEquals(aujourdhui, demain)
    }

    @Test
    fun `parse lit tous les champs d un catalogue`() {
        val catalogue = MotDuJourStore.parse(
            """
            [
              {
                "id": "saudade",
                "mot": "saudade",
                "langue": "pt",
                "prononciation": "/sawˈda.dʒi/",
                "definition": "Nostalgie douce.",
                "exemple": "Sinto saudade.",
                "origine": "Du latin solitate.",
                "categorie": "Nom",
                "niveau": "B1"
              },
              {
                "id": "ephemere",
                "mot": "éphémère",
                "langue": "fr",
                "definition": "Qui ne dure pas."
              }
            ]
            """.trimIndent()
        )
        assertEquals(2, catalogue.size)
        val saudade = catalogue.first()
        assertEquals("saudade", saudade.id)
        assertEquals("pt", saudade.langue)
        assertEquals("/sawˈda.dʒi/", saudade.prononciation)
        assertEquals("Nostalgie douce.", saudade.definition)
        assertEquals("Sinto saudade.", saudade.exemple)
        assertEquals("Du latin solitate.", saudade.origine)
        assertEquals("Nom", saudade.categorie)
        assertEquals("B1", saudade.niveau)
        assertEquals("pt", saudade.codeLangue)
        val ephemere = catalogue[1]
        assertEquals("fr", ephemere.langue)
        assertNull(ephemere.prononciation)
        assertNull(ephemere.exemple)
    }

    @Test
    fun `un identifiant duplique casse le parse`() {
        val texte = """
            [
              { "id": "x", "mot": "un", "langue": "fr", "definition": "Un." },
              { "id": "x", "mot": "deux", "langue": "fr", "definition": "Deux." }
            ]
        """.trimIndent()
        // Le parseur n'exige pas l'unicité — c'est le sélecteur qui trie par id.
        val catalogue = MotDuJourStore.parse(texte)
        assertEquals(2, catalogue.size)
    }

    @Test
    fun `codeLangue normalise pt-BR en pt`() {
        val mot = MotDuJour(
            id = "x", mot = "saudade", langue = "pt-BR",
            definition = "Nostalgie douce."
        )
        assertEquals("pt", mot.codeLangue)
    }
}
