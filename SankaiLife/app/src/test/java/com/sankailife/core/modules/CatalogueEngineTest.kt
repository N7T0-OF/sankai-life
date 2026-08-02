package com.sankailife.core.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueEngineTest {

    private fun entree(
        id: String = "portugais-a1-bases",
        nom: String = "Portugais A1 — Bases",
        langue: String = "pt",
        cartes: Int = 87,
        octets: Long = 1548,
        empreinte: String = "a".repeat(64),
        url: String = "https://raw.githubusercontent.com/x/y/main/modules/paquets/p.zip"
    ) = CatalogueEngine.Entree(
        id = id, nom = nom, langue = langue, cartes = cartes,
        octets = octets, empreinte = empreinte, url = url
    )

    // --- Refus ----------------------------------------------------------------

    @Test
    fun `une entree complete est acceptee`() {
        assertNull(CatalogueEngine.refus(entree()))
    }

    @Test
    fun `une adresse non securisee est refusee`() {
        // Un catalogue est un fichier distant qui contient des adresses : sans
        // cette barriere, une entree modifiee ferait telecharger n'importe quoi.
        val v = CatalogueEngine.refus(entree(url = "http://exemple.test/p.zip"))
        assertNotNull(v)
        assertTrue(v!!.contains("sécurisée"))
    }

    @Test
    fun `une entree sans empreinte est refusee`() {
        val v = CatalogueEngine.refus(entree(empreinte = ""))
        assertNotNull(v)
        assertTrue(v!!.contains("Empreinte"))
    }

    @Test
    fun `une empreinte de mauvaise longueur est refusee`() {
        assertNotNull(CatalogueEngine.refus(entree(empreinte = "abc")))
    }

    @Test
    fun `un module demesure est refuse avant tout telechargement`() {
        // Faire descendre deux megaoctets pour annoncer ensuite que l'entree
        // est invalide gaspille la connexion de quelqu'un.
        val v = CatalogueEngine.refus(entree(octets = CatalogueEngine.MAX_OCTETS + 1))
        assertNotNull(v)
        assertTrue(v!!.contains("démesuré"))
    }

    @Test
    fun `une entree sans carte ou sans nom est refusee`() {
        assertNotNull(CatalogueEngine.refus(entree(cartes = 0)))
        assertNotNull(CatalogueEngine.refus(entree(nom = "")))
        assertNotNull(CatalogueEngine.refus(entree(id = "")))
        assertNotNull(CatalogueEngine.refus(entree(octets = 0)))
    }

    // --- Verification du recu -------------------------------------------------

    @Test
    fun `un fichier conforme passe`() {
        val e = entree()
        assertNull(CatalogueEngine.verifierRecu(e, e.octets, e.empreinte))
    }

    @Test
    fun `un telechargement incomplet est detecte`() {
        // Sans cette verification, la moitie des cartes d'un cours
        // s'installerait sans qu'aucun message ne le dise.
        val e = entree()
        val v = CatalogueEngine.verifierRecu(e, e.octets - 10, e.empreinte)
        assertNotNull(v)
        assertTrue(v!!.contains("incomplet"))
    }

    @Test
    fun `une empreinte differente est refusee`() {
        val e = entree()
        assertNotNull(CatalogueEngine.verifierRecu(e, e.octets, "b".repeat(64)))
    }

    @Test
    fun `l'empreinte se compare sans tenir compte de la casse`() {
        val e = entree(empreinte = "ABCDEF" + "0".repeat(58))
        assertNull(CatalogueEngine.verifierRecu(e, e.octets, e.empreinte.lowercase()))
    }

    // --- Affichage ------------------------------------------------------------

    @Test
    fun `la taille est lisible`() {
        assertEquals("512 o", entree(octets = 512).taille)
        assertEquals("2 ko", entree(octets = 2048).taille)
    }

    @Test
    fun `les details disent le niveau le nombre de cartes et la taille`() {
        val e = CatalogueEngine.Entree(
            id = "x", nom = "X", langue = "pt", niveau = "A1",
            cartes = 87, octets = 1548, empreinte = "a".repeat(64),
            url = "https://raw.githubusercontent.com/a/b.zip"
        )
        assertTrue(e.details.contains("A1"))
        assertTrue(e.details.contains("87 cartes"))
        assertTrue(e.details.contains("ko"))
    }

    @Test
    fun `un module sans niveau n'en invente pas`() {
        val e = entree(langue = "").copy(niveau = "")
        assertFalse(e.details.contains("A1"))
    }

    // --- Classement -----------------------------------------------------------

    @Test
    fun `les langues passent avant les autres matieres`() {
        val classe = CatalogueEngine.classer(
            listOf(
                entree(id = "blender", nom = "Blender", langue = ""),
                entree(id = "pt", nom = "Portugais", langue = "pt")
            )
        )
        assertEquals(CatalogueEngine.Famille.LANGUES, classe.first().first)
        assertEquals(CatalogueEngine.Famille.AUTRES, classe.last().first)
    }

    @Test
    fun `les entrees invalides sont ecartees une fois pour toutes`() {
        // Les laisser passer obligerait chaque ecran a refaire la verification,
        // et l'un d'eux finirait par l'oublier.
        val classe = CatalogueEngine.classer(
            listOf(entree(), entree(id = "casse", url = "http://x/y.zip"))
        )
        assertEquals(1, classe.sumOf { it.second.size })
    }

    @Test
    fun `un catalogue vide ne fait pas planter`() {
        assertTrue(CatalogueEngine.classer(emptyList()).isEmpty())
    }

    @Test
    fun `un module deja installe est reconnu malgre la casse`() {
        assertTrue(
            CatalogueEngine.estInstalle(
                entree(nom = "Portugais A1"), setOf("  portugais a1  ")
            )
        )
        assertFalse(CatalogueEngine.estInstalle(entree(nom = "Portugais A1"), setOf("Anglais")))
        assertFalse(CatalogueEngine.estInstalle(entree(), emptySet()))
    }
}
