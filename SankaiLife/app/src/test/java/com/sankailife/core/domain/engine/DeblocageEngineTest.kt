package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeblocageEngineTest {

    @Test
    fun `rien d'essentiel n'est verrouille au niveau 1`() {
        // Une application d'apprentissage qui fait attendre avant d'apprendre
        // a raté son objet. C'est la règle la plus importante de ce moteur.
        val ouvertes = DeblocageEngine.Fonction.ouvertes(1)
        assertTrue(DeblocageEngine.Fonction.MEMO in ouvertes)
        assertTrue(DeblocageEngine.Fonction.FLASHCARDS in ouvertes)
        assertTrue(DeblocageEngine.Fonction.JARDIN in ouvertes)
        assertTrue(DeblocageEngine.Fonction.COFFRE_QUOTIDIEN in ouvertes)
    }

    @Test
    fun `le nombre de fonctions ouvertes ne fait que croitre`() {
        var precedent = 0
        for (niveau in 1..12) {
            val n = DeblocageEngine.Fonction.ouvertes(niveau).size
            assertTrue("regression au niveau $niveau", n >= precedent)
            precedent = n
        }
    }

    @Test
    fun `tout est ouvert au dernier palier`() {
        val maxRequis = DeblocageEngine.Fonction.entries.maxOf { it.niveauRequis }
        assertEquals(
            DeblocageEngine.Fonction.entries.size,
            DeblocageEngine.Fonction.ouvertes(maxRequis).size
        )
        assertNull(DeblocageEngine.Fonction.prochaine(maxRequis))
    }

    @Test
    fun `la prochaine fonction est bien la plus proche`() {
        val p = DeblocageEngine.Fonction.prochaine(1)
        assertNotNull(p)
        assertEquals(2, p!!.niveauRequis)
    }

    @Test
    fun `un verrou explique toujours ce qui manque`() {
        // Un cadenas muet n'est pas une progression, c'est une frustration.
        val v = DeblocageEngine.verrou(DeblocageEngine.Fonction.DEFIS, niveau = 3)
        assertNotNull(v)
        assertEquals(2, v!!.niveauxRestants)
        assertTrue(v.explication.contains("5"))
        assertTrue(v.explication.contains("3"))
        assertTrue(v.explication.contains("2 niveaux"))
        assertTrue(v.titre.contains("Défis"))
    }

    @Test
    fun `le singulier est respecte quand il ne reste qu'un niveau`() {
        val v = DeblocageEngine.verrou(DeblocageEngine.Fonction.DEFIS, niveau = 4)!!
        assertTrue(v.explication.contains("1 niveau."))
    }

    @Test
    fun `une fonction ouverte n'a pas de verrou`() {
        assertNull(DeblocageEngine.verrou(DeblocageEngine.Fonction.MEMO, niveau = 1))
        assertTrue(DeblocageEngine.estDebloquee(DeblocageEngine.Fonction.DEFIS, 5))
        assertTrue(!DeblocageEngine.estDebloquee(DeblocageEngine.Fonction.DEFIS, 4))
    }

    @Test
    fun `chaque fonction est decrite`() {
        for (f in DeblocageEngine.Fonction.entries) {
            assertTrue(f.libelle.isNotBlank())
            assertTrue(f.description.isNotBlank())
            assertTrue(f.niveauRequis >= 1)
        }
    }
}
