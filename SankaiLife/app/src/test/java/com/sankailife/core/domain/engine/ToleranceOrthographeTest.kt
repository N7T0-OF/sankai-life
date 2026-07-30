package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToleranceOrthographeTest {

    @Test
    fun `les accents oublies sont acceptes`() {
        // La faute la plus fréquente sur un clavier de téléphone, et la moins
        // significative : la refuser découragerait plus qu'elle n'enseignerait.
        assertTrue(ToleranceOrthographe.estAcceptee("etre", "être"))
        assertTrue(ToleranceOrthographe.estAcceptee("elève", "élève"))
    }

    @Test
    fun `la casse et la ponctuation sont ignorees`() {
        assertTrue(ToleranceOrthographe.estAcceptee("TOKYO", "Tokyo"))
        assertTrue(ToleranceOrthographe.estAcceptee("l'eau", "leau"))
        assertTrue(ToleranceOrthographe.estAcceptee("  Bonjour !  ", "Bonjour"))
    }

    @Test
    fun `une reponse exacte est distinguee d'une reponse tolerée`() {
        assertEquals(
            ToleranceOrthographe.Verdict.EXACT,
            ToleranceOrthographe.corriger("Tokyo", "Tokyo")
        )
        assertEquals(
            ToleranceOrthographe.Verdict.ACCEPTE_AVEC_FAUTES,
            ToleranceOrthographe.corriger("anniversaire", "anniverssaire")
        )
    }

    @Test
    fun `une reponse fausse reste fausse`() {
        assertFalse(ToleranceOrthographe.estAcceptee("Osaka", "Tokyo"))
        assertFalse(ToleranceOrthographe.estAcceptee("chien", "chat"))
    }

    @Test
    fun `un mot court ne tolere aucune faute`() {
        // 3 caractères : 3 / 6 = 0 faute tolérée. « chat » et « char » doivent
        // rester distincts, sinon les réponses courtes ne testent plus rien.
        assertEquals(0, ToleranceOrthographe.fautesTolerees(4))
        assertFalse(ToleranceOrthographe.estAcceptee("char", "chat"))
    }

    @Test
    fun `une phrase longue tolere plus, mais pas tout`() {
        val attendu = "la capitale du japon est tokyo"
        assertTrue(ToleranceOrthographe.estAcceptee("la capitale du japon est tokio", attendu))
        assertFalse(ToleranceOrthographe.estAcceptee("la capitale de la chine", attendu))
    }

    @Test
    fun `la tolerance est plafonnee`() {
        // Sans plafond, un texte de deux cents caractères accepterait
        // n'importe quoi qui lui ressemble vaguement.
        assertEquals(3, ToleranceOrthographe.fautesTolerees(500))
    }

    @Test
    fun `une reponse vide est toujours refusee`() {
        assertFalse(ToleranceOrthographe.estAcceptee("", "Tokyo"))
        assertFalse(ToleranceOrthographe.estAcceptee("   ", "Tokyo"))
    }

    @Test
    fun `la distance est symetrique et nulle sur l'identite`() {
        assertEquals(0, ToleranceOrthographe.distance("chat", "chat"))
        assertEquals(
            ToleranceOrthographe.distance("chat", "chien"),
            ToleranceOrthographe.distance("chien", "chat")
        )
        assertEquals(4, ToleranceOrthographe.distance("", "chat"))
    }
}
