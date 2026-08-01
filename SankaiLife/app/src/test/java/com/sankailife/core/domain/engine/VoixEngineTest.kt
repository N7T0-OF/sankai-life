package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoixEngineTest {

    @Test
    fun `une langue non declaree ne donne pas de locale`() {
        // Faire lire du portugais par une voix française produirait un son que
        // personne ne reconnaîtrait, sans que l'apprenant puisse s'en douter.
        assertNull(VoixEngine.locale(""))
        assertNull(VoixEngine.locale("   "))
    }

    @Test
    fun `une langue declaree donne la bonne locale`() {
        assertEquals("pt", VoixEngine.locale("pt")?.language)
        assertEquals("pt", VoixEngine.locale("pt-BR")?.language)
        assertEquals("BR", VoixEngine.locale("pt-BR")?.country)
        assertNotNull(VoixEngine.locale("fr"))
    }

    @Test
    fun `un code invalide ne fait pas planter`() {
        // Un manifeste vient de l'extérieur : il peut contenir n'importe quoi.
        assertNull(VoixEngine.locale("!!!"))
        assertNull(VoixEngine.locale("1234"))
    }

    @Test
    fun `les precisions entre parentheses ne sont pas prononcees`() {
        // « Obrigado (dit par un homme) » doit s'entendre « Obrigado » : la
        // précision s'adresse au lecteur, pas à l'oreille.
        assertEquals("Obrigado", VoixEngine.aPrononcer("Obrigado (dit par un homme)"))
        assertEquals("Bom dia", VoixEngine.aPrononcer("Bom dia (le matin)"))
    }

    @Test
    fun `les espaces surnumeraires sont resserres`() {
        assertEquals("Bom dia", VoixEngine.aPrononcer("  Bom    dia \n"))
    }

    @Test
    fun `une carte qui n'est que parentheses ne se prononce pas`() {
        assertNull(VoixEngine.aPrononcer("(sans contenu)"))
        assertNull(VoixEngine.aPrononcer("   "))
    }

    @Test
    fun `un texte demesure est tronque`() {
        val long = "a".repeat(VoixEngine.MAX_CARACTERES * 3)
        assertEquals(VoixEngine.MAX_CARACTERES, VoixEngine.aPrononcer(long)!!.length)
    }

    @Test
    fun `les trois conditions sont exigees ensemble`() {
        // Un bouton qui apparaît et ne produit aucun son est pire que pas de
        // bouton du tout.
        assertTrue(VoixEngine.peutParler("pt", "Olá", moteurPret = true))
        assertFalse(VoixEngine.peutParler("pt", "Olá", moteurPret = false))
        assertFalse(VoixEngine.peutParler("", "Olá", moteurPret = true))
        assertFalse(VoixEngine.peutParler("pt", "( )", moteurPret = true))
    }

    @Test
    fun `on n'explique une absence de voix que si une langue etait connue`() {
        // Sans langue déclarée, l'écoute n'a jamais été promise : annoncer une
        // voix manquante inventerait un problème.
        assertEquals(
            "Voix indisponible sur cet appareil.",
            VoixEngine.raisonIndisponible("pt", moteurPret = false)
        )
        assertNull(VoixEngine.raisonIndisponible("", moteurPret = false))
        assertNull(VoixEngine.raisonIndisponible("pt", moteurPret = true))
    }
}
