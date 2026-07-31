package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConseilEngineTest {

    @Test
    fun `un jardin sans probleme ne dit rien`() {
        // La bulle disparaît au lieu d'inventer une phrase. Un conseil qui
        // s'affiche toujours est un conseil qu'on n'ouvre jamais.
        assertNull(ConseilEngine.choisir(ConseilEngine.Contexte(eau = 20)))
    }

    @Test
    fun `le terrain sature passe avant tout`() {
        val c = ConseilEngine.choisir(
            ConseilEngine.Contexte(
                terrainSature = true, cartesDues = 30, eau = 0, parcellesPretes = 5
            )
        )
        assertEquals(ConseilEngine.Type.DEPOT_PLEIN, c?.type)
    }

    @Test
    fun `les cartes dues passent avant l'agriculture`() {
        // C'est le seul endroit du code où cette hiérarchie est affirmée, et
        // elle est délibérée : le jardin sert l'apprentissage, pas l'inverse.
        val c = ConseilEngine.choisir(
            ConseilEngine.Contexte(
                cartesDues = 12, parcellesPretes = 4, parcellesSeches = 6,
                eau = 50, valeurStock = 900, magasinOuvert = true
            )
        )
        assertEquals(ConseilEngine.Type.CARTES_DUES, c?.type)
        assertTrue(c!!.texte.contains("12"))
    }

    @Test
    fun `sans eau, le conseil renvoie vers la revision`() {
        val c = ConseilEngine.choisir(ConseilEngine.Contexte(eau = 0, parcellesSeches = 3))
        assertEquals(ConseilEngine.Type.PLUS_D_EAU, c?.type)
        assertEquals("Réviser", c?.action)
    }

    @Test
    fun `la pluie prime sur la soif`() {
        // Arroser juste avant une averse gaspille une eau qu'il a fallu gagner.
        val c = ConseilEngine.choisir(
            ConseilEngine.Contexte(eau = 30, parcellesSeches = 5, ilVaPleuvoir = true)
        )
        assertEquals(ConseilEngine.Type.PLUIE_ATTENDUE, c?.type)
    }

    @Test
    fun `sans pluie, la soif est signalee`() {
        val c = ConseilEngine.choisir(
            ConseilEngine.Contexte(eau = 30, parcellesSeches = 5, ilVaPleuvoir = false)
        )
        assertEquals(ConseilEngine.Type.PARCELLES_SECHES, c?.type)
        assertTrue(c!!.texte.contains("5"))
    }

    @Test
    fun `le stock ne se signale que si le marchand est la`() {
        val ferme = ConseilEngine.choisir(
            ConseilEngine.Contexte(eau = 30, valeurStock = 400, magasinOuvert = false)
        )
        assertNull(ferme)

        val ouvert = ConseilEngine.choisir(
            ConseilEngine.Contexte(eau = 30, valeurStock = 400, magasinOuvert = true)
        )
        assertEquals(ConseilEngine.Type.STOCK_VENDABLE, ouvert?.type)
    }

    @Test
    fun `les Mimos affames ne sont signales que s'il y en a`() {
        assertNull(
            ConseilEngine.choisir(
                ConseilEngine.Contexte(eau = 30, compost = 0, nombreMimos = 0)
            )
        )
        val c = ConseilEngine.choisir(
            ConseilEngine.Contexte(eau = 30, compost = 0, nombreMimos = 3)
        )
        assertEquals(ConseilEngine.Type.MIMOS_AFFAMES, c?.type)
    }

    @Test
    fun `un seul conseil est rendu, jamais plusieurs`() {
        // Le type de retour l'impose, mais le test documente l'intention :
        // empiler les conseils rendrait la bulle illisible.
        val c = ConseilEngine.choisir(
            ConseilEngine.Contexte(
                cartesDues = 5, eau = 0, parcellesPretes = 3, parcellesSeches = 3,
                valeurStock = 100, magasinOuvert = true, compost = 0, nombreMimos = 2
            )
        )
        assertNotNull(c)
    }

    @Test
    fun `la capsule ne s'affiche que s'il reste des cartes`() {
        assertTrue(!ConseilEngine.Capsule(0, 0).visible)
        assertTrue(ConseilEngine.Capsule(3, 10).visible)
        assertEquals("3 / 10", ConseilEngine.Capsule(3, 10).libelle)
        assertTrue(ConseilEngine.Capsule(10, 10).terminee)
    }
}
