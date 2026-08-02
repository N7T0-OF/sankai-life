package com.sankailife.core.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartageEntrantTest {

    /**
     * Partager un lien depuis un navigateur arrive tout le temps.
     *
     * L'importer comme carte donnerait une carte dont le recto est une URL, ce
     * que personne ne veut. On le reconnait pour pouvoir le dire.
     */
    @Test
    fun `une adresse seule est reconnue`() {
        assertTrue(PartageEntrant.estUneAdresse("https://exemple.test/module.zip"))
        assertTrue(PartageEntrant.estUneAdresse("  http://exemple.test  "))
    }

    @Test
    fun `des cartes ne sont pas prises pour une adresse`() {
        assertFalse(PartageEntrant.estUneAdresse("Olá | Bonjour"))
        assertFalse(PartageEntrant.estUneAdresse("casa :: maison\nagua :: eau"))
    }

    @Test
    fun `un texte de plusieurs lignes contenant une adresse reste du texte`() {
        // Quelqu'un peut tres bien avoir une carte qui parle d'un site.
        val contenu = "Site officiel | https://exemple.test\nAutre | chose"
        assertFalse(PartageEntrant.estUneAdresse(contenu))
    }

    @Test
    fun `une ligne avec une adresse et du texte reste du texte`() {
        assertFalse(PartageEntrant.estUneAdresse("voir https://exemple.test"))
    }

    @Test
    fun `un contenu vide n'est pas une adresse`() {
        assertFalse(PartageEntrant.estUneAdresse(""))
        assertFalse(PartageEntrant.estUneAdresse("   "))
    }

    @Test
    fun `le porteur se vide quand on le consomme`() {
        // Sans cela, revenir sur l'Academie rouvrirait indefiniment le meme
        // apercu d'import.
        PartageEntrant.deposer(PartageEntrant.Contenu.Texte("a | b"))
        assertTrue(PartageEntrant.recu.value != null)
        PartageEntrant.consommer()
        assertTrue(PartageEntrant.recu.value == null)
    }
}
