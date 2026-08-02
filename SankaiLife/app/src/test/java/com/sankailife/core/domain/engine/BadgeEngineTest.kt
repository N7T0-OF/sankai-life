package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {

    private val defis = DeblocageEngine.Fonction.DEFIS

    /**
     * Le defaut exact : l'onglet Defis se debloque au niveau 5, et au niveau 2
     * il portait quand meme sa pastille « 3 ». On annoncait trois choses a
     * reclamer derriere une porte qu'on ne peut pas ouvrir.
     */
    @Test
    fun `une section verrouillee ne porte aucun badge`() {
        assertFalse(BadgeEngine.afficher(defis, niveau = 2, compte = 3))
        assertEquals(0, BadgeEngine.compte(defis, niveau = 2, compte = 3))
    }

    @Test
    fun `la meme section debloquee porte son badge`() {
        assertTrue(BadgeEngine.afficher(defis, niveau = defis.niveauRequis, compte = 3))
        assertEquals(3, BadgeEngine.compte(defis, niveau = defis.niveauRequis, compte = 3))
    }

    @Test
    fun `le niveau exact suffit`() {
        // Un verrou qui exige « strictement plus » decalerait tout d'un niveau
        // sans que rien ne l'annonce.
        assertTrue(BadgeEngine.afficher(defis, niveau = 5, compte = 1))
        assertFalse(BadgeEngine.afficher(defis, niveau = 4, compte = 1))
    }

    @Test
    fun `rien a signaler ne montre rien`() {
        assertFalse(BadgeEngine.afficher(defis, niveau = 10, compte = 0))
        assertFalse(BadgeEngine.afficher(null, niveau = 10, compte = 0))
        assertEquals(0, BadgeEngine.compte(null, niveau = 10, compte = 0))
    }

    @Test
    fun `un compte negatif ne montre rien`() {
        // Une soustraction ratee ailleurs ne doit pas peindre une pastille.
        assertFalse(BadgeEngine.afficher(null, niveau = 10, compte = -2))
    }

    @Test
    fun `ce qui n'est jamais verrouille garde son badge`() {
        // L'accueil et le profil n'ont pas de verrou : leur passer null ne doit
        // pas les priver de pastille.
        assertTrue(BadgeEngine.afficher(null, niveau = 1, compte = 2))
        assertEquals(2, BadgeEngine.compte(null, niveau = 1, compte = 2))
    }

    @Test
    fun `la route des defis est la seule verrouillee de la barre`() {
        // Le tableau vit avec la regle : les separer etait la cause du defaut,
        // le verrou servant a griser l'onglet et etant ignore pour la pastille.
        assertEquals(defis, BadgeEngine.fonctionDeRoute("challenges"))
        assertEquals(null, BadgeEngine.fonctionDeRoute("home"))
        assertEquals(null, BadgeEngine.fonctionDeRoute("shop"))
        assertEquals(null, BadgeEngine.fonctionDeRoute(null))
    }

    @Test
    fun `toute fonction verrouillee masque son badge quel que soit le niveau`() {
        DeblocageEngine.Fonction.entries.forEach { f ->
            val avant = f.niveauRequis - 1
            if (avant >= 0) {
                assertFalse("$f affiche un badge au niveau $avant",
                    BadgeEngine.afficher(f, avant, 5))
            }
            assertTrue("$f masque son badge une fois debloquee",
                BadgeEngine.afficher(f, f.niveauRequis, 5))
        }
    }
}
