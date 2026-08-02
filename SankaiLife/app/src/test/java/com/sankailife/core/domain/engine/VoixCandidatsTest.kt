package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VoixCandidatsTest {

    /**
     * « pt » n'est pas une prononciation.
     *
     * La plupart des moteurs Android resolvent le portugais generique en
     * bresilien. Or le contenu livre est du portugais europeen — « comboio »,
     * « autocarro » — et l'entendre avec l'accent bresilien apprend une
     * prononciation qui ne correspond pas aux mots qu'on lit.
     */
    @Test
    fun `le portugais sans region essaie le Portugal en premier`() {
        val candidats = VoixEngine.candidats("pt")
        assertEquals("PT", candidats.first().country)
        assertTrue("Le bresilien doit rester un repli", candidats.any { it.country == "BR" })
    }

    @Test
    fun `une region declaree est respectee`() {
        // Un module bresilien doit sonner bresilien : la regle ne devine que
        // pour le code sans region.
        val candidats = VoixEngine.candidats("pt-BR")
        assertEquals(1, candidats.size)
        assertEquals("BR", candidats.first().country)
    }

    @Test
    fun `les autres langues gardent leur comportement`() {
        // Rien ne doit changer pour l'anglais ou le francais : on ne devine que
        // la ou on a une raison de le faire.
        assertEquals(listOf(Locale.forLanguageTag("en")), VoixEngine.candidats("en"))
        assertEquals(listOf(Locale.forLanguageTag("fr")), VoixEngine.candidats("fr"))
    }

    @Test
    fun `un code vide ne propose rien`() {
        assertTrue(VoixEngine.candidats("").isEmpty())
        assertTrue(VoixEngine.candidats("   ").isEmpty())
    }

    @Test
    fun `l'ordre place toujours le plus precis en tete`() {
        VoixEngine.candidats("pt").let { liste ->
            assertTrue(
                "Une locale sans region ne doit pas passer devant",
                liste.indexOfFirst { it.country.isBlank() } == liste.size - 1
            )
        }
    }
}
