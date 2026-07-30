package com.sankailife.core.garden.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoistureEngineTest {

    private val tournesol = seedParId("tournesol")!!
    private val cactus = seedParId("cactus")!!
    private val menthe = seedParId("menthe")!!

    @Test
    fun `l'humidite couvre tout l'intervalle sans trou`() {
        var v = 0f
        while (v <= 1f) {
            MoistureEngine.etat(v)
            v += 0.01f
        }
    }

    @Test
    fun `un sol sec ralentit sans jamais bloquer`() {
        val facteur = MoistureEngine.facteurCroissance(0.05f, tournesol)
        assertTrue("le sol sec doit ralentir", facteur < 1f)
        // La règle qui ne bouge pas : aucune plante ne meurt, aucune ne
        // s'arrête. Un facteur nul serait une punition déguisée.
        assertTrue("mais jamais arreter", facteur > 0.5f)
    }

    @Test
    fun `bien arroser donne un bonus modeste`() {
        assertTrue(MoistureEngine.facteurCroissance(0.8f, tournesol) > 1f)
        assertTrue(MoistureEngine.facteurCroissance(0.8f, tournesol) < 1.2f)
    }

    @Test
    fun `un sol detrempe penalise aussi`() {
        // Sinon la stratégie optimale serait d'arroser sans réfléchir.
        assertTrue(MoistureEngine.facteurCroissance(1f, tournesol) < 1f)
    }

    @Test
    fun `le cactus prefere le sec, exactement a l'inverse du tournesol`() {
        // C'est ce qui rend le choix des graines stratégique : un cactus n'est
        // pas un tournesol qu'on arrose moins.
        assertTrue(
            MoistureEngine.facteurCroissance(0.25f, cactus) >
                MoistureEngine.facteurCroissance(0.25f, tournesol)
        )
        assertTrue(
            MoistureEngine.facteurCroissance(1f, cactus) <
                MoistureEngine.facteurCroissance(1f, tournesol)
        )
    }

    @Test
    fun `le sable seche plus vite que la terre riche`() {
        val sable = MoistureEngine.apresEcoulement(1f, 600, SoilType.SABLE)
        val riche = MoistureEngine.apresEcoulement(1f, 600, SoilType.RICHE)
        assertTrue(sable < riche)
    }

    @Test
    fun `la nuit seche moins vite`() {
        val jour = MoistureEngine.apresEcoulement(1f, 480, SoilType.TERRE, partNocturne = 0f)
        val nuit = MoistureEngine.apresEcoulement(1f, 480, SoilType.TERRE, partNocturne = 1f)
        assertTrue(nuit > jour)
    }

    @Test
    fun `l'humidite reste bornee entre zero et un`() {
        assertEquals(0f, MoistureEngine.apresEcoulement(0.1f, 100_000, SoilType.SABLE), 0.001f)
        assertEquals(1f, MoistureEngine.apresArrosage(0.95f), 0.001f)
        assertEquals(0.5f, MoistureEngine.apresEcoulement(0.5f, 0, SoilType.TERRE), 0.001f)
    }

    @Test
    fun `un arrosage remonte le sol d'un cran au moins`() {
        val avant = 0.2f
        val apres = MoistureEngine.apresArrosage(avant)
        assertTrue(apres > avant)
        assertTrue(MoistureEngine.etat(apres) != MoistureEngine.etat(avant))
    }

    @Test
    fun `le seuil de soif depend de l'espece`() {
        // À humidité égale, la menthe a soif et le cactus non.
        assertTrue(MoistureEngine.aBesoinDEau(0.5f, menthe))
        assertFalse(MoistureEngine.aBesoinDEau(0.5f, cactus))
    }

    @Test
    fun `l'annonce de secheresse est nulle sur un sol deja sec`() {
        assertEquals(0f, MoistureEngine.heuresAvantSecheresse(0.10f, SoilType.TERRE), 0.001f)
        assertTrue(MoistureEngine.heuresAvantSecheresse(1f, SoilType.TERRE) > 0f)
    }

    @Test
    fun `la teinte s'assombrit quand l'humidite monte`() {
        // Le sol doit rester lisible d'un coup d'œil : plus foncé = plus humide,
        // sans exception, sinon la couleur ne veut plus rien dire.
        val sec = MoistureEngine.teinteSol(0.05f)
        val humide = MoistureEngine.teinteSol(0.5f)
        val trempe = MoistureEngine.teinteSol(1f)
        assertTrue(sec > humide)
        assertTrue(humide > trempe)
    }
}
