package com.sankailife.core.modules

import com.sankailife.core.modules.FormatImportEngine.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatImportEngineTest {

    private fun detecter(texte: String, nom: String = "") =
        FormatImportEngine.detecter(texte.toByteArray(), nom)

    // --- Detection ------------------------------------------------------------

    @Test
    fun `une archive est reconnue a sa signature`() {
        // Deux octets, et ils ne se confondent avec rien.
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)
        assertEquals(Format.ARCHIVE, FormatImportEngine.detecter(zip))
    }

    @Test
    fun `un json est reconnu a sa premiere accolade`() {
        assertEquals(Format.JSON, detecter("""{"id":"x"}"""))
        assertEquals(Format.JSON, detecter("""[{"id":"x"}]"""))
        // Espaces et sauts de ligne en tete : un fichier partage par messagerie
        // en gagne souvent.
        assertEquals(Format.JSON, detecter("  \n  {\"id\":\"x\"}"))
        // Marque d'ordre des octets, que tous les editeurs Windows ajoutent.
        assertEquals(Format.JSON, detecter("﻿{\"id\":\"x\"}"))
    }

    @Test
    fun `un fichier texte avec separateurs est reconnu`() {
        assertEquals(Format.TEXTE, detecter("Olá | Bonjour\nAdeus | Au revoir"))
        assertEquals(Format.TEXTE, detecter("casa :: maison\nagua :: eau"))
        assertEquals(Format.TEXTE, detecter("casa\tmaison\nagua\teau"))
    }

    @Test
    fun `un csv est reconnu a ses virgules`() {
        assertEquals(Format.CSV, detecter("front,back\ncasa,maison\nagua,eau"))
    }

    @Test
    fun `une virgule isolee ne fait pas d'un texte un tableur`() {
        // « Bonjour, salut » contient une virgule et reste une carte.
        val contenu = "Olá | Bonjour, salut\nAdeus | Au revoir\nBom dia | Bonjour"
        assertEquals(Format.TEXTE, detecter(contenu))
    }

    @Test
    fun `le format vient du contenu pas de l'extension`() {
        // Un fichier partage depuis une autre application arrive souvent sans
        // nom utilisable, et un .txt qui contient du JSON reste du JSON.
        assertEquals(Format.JSON, detecter("""{"id":"x"}""", "cartes.txt"))
        assertEquals(Format.TEXTE, detecter("a | b\nc | d", "module.json"))
    }

    @Test
    fun `un contenu vide n'est pas reconnu`() {
        assertEquals(Format.INCONNU, detecter(""))
        assertEquals(Format.INCONNU, detecter("   \n  \n "))
    }

    @Test
    fun `un texte sans separateur reste du texte`() {
        // Des phrases a se remémorer : c'est un usage reel du Memo.
        assertEquals(Format.TEXTE, detecter("Respire\nAvance\nRecommence"))
    }

    // --- Lecture texte --------------------------------------------------------

    @Test
    fun `chaque ligne devient une carte`() {
        val cartes = FormatImportEngine.lireTexte("Olá | Bonjour\nAdeus | Au revoir")
        assertEquals(2, cartes.size)
        assertEquals("Olá :: Bonjour", cartes[0])
    }

    @Test
    fun `les separateurs peuvent se melanger dans un fichier`() {
        // Un fichier assemble a la main depuis plusieurs sources.
        val cartes = FormatImportEngine.lireTexte("a | b\nc :: d\ne\tf")
        assertEquals(listOf("a :: b", "c :: d", "e :: f"), cartes)
    }

    @Test
    fun `les lignes vides et les commentaires sont ignores`() {
        // Quelqu'un qui commente son fichier ne veut pas voir ses commentaires
        // en revision.
        val cartes = FormatImportEngine.lireTexte("# Salutations\n\nOlá | Bonjour\n\n")
        assertEquals(1, cartes.size)
    }

    @Test
    fun `une ligne sans separateur reste une carte d'une seule face`() {
        val cartes = FormatImportEngine.lireTexte("Respire profondement")
        assertEquals(listOf("Respire profondement"), cartes)
    }

    @Test
    fun `un verso vide ne fabrique pas une carte a moitie`() {
        assertEquals(listOf("Olá"), FormatImportEngine.lireTexte("Olá | "))
    }

    @Test
    fun `la limite de cartes est respectee`() {
        val gros = (1..100).joinToString("\n") { "mot$it | traduction$it" }
        assertEquals(10, FormatImportEngine.lireTexte(gros, limite = 10).size)
    }

    // --- Lecture CSV ----------------------------------------------------------

    @Test
    fun `un csv simple se lit`() {
        val cartes = FormatImportEngine.lireCsv("casa,maison\nagua,eau")
        assertEquals(listOf("casa :: maison", "agua :: eau"), cartes)
    }

    @Test
    fun `l'entete est ecartee`() {
        val cartes = FormatImportEngine.lireCsv("Front,Back\ncasa,maison")
        assertEquals(1, cartes.size)
        assertEquals("casa :: maison", cartes[0])
    }

    @Test
    fun `une premiere ligne qui n'est pas une entete est conservee`() {
        // Perdre la premiere carte d'un fichier sans entete serait pire que
        // garder une ligne d'entete.
        val cartes = FormatImportEngine.lireCsv("casa,maison\nagua,eau")
        assertEquals(2, cartes.size)
    }

    @Test
    fun `les guillemets protegent les virgules`() {
        // Tous les tableurs en produisent des qu'une traduction contient une
        // virgule, et decouper dessus donnerait une carte fausse.
        val cartes = FormatImportEngine.lireCsv("\"casa\",\"maison, foyer\"")
        assertEquals(listOf("casa :: maison, foyer"), cartes)
    }

    @Test
    fun `les guillemets doubles se lisent comme un guillemet`() {
        val ligne = "\"dire \"\"bonjour\"\"\",\"saluer\""
        assertEquals(
            listOf("dire \"bonjour\" :: saluer"),
            FormatImportEngine.lireCsv(ligne)
        )
    }

    @Test
    fun `le point-virgule fonctionne comme separateur`() {
        // C'est le separateur des tableurs francais.
        assertEquals(listOf("casa :: maison"), FormatImportEngine.lireCsv("casa;maison"))
    }

    @Test
    fun `les colonnes en trop sont ignorees`() {
        // Anki et Quizlet ajoutent etiquettes et statistiques, qui ne veulent
        // rien dire ici.
        val cartes = FormatImportEngine.lireCsv("casa,maison,tag1,42,2024-01-01")
        assertEquals(listOf("casa :: maison"), cartes)
    }

    @Test
    fun `une ligne incomplete est ecartee sans faire echouer le reste`() {
        val cartes = FormatImportEngine.lireCsv("casa,maison\nseul\nagua,eau")
        assertEquals(2, cartes.size)
    }

    @Test
    fun `le decoupage csv gere une ligne vide`() {
        assertEquals(listOf(""), FormatImportEngine.decouperCsv(""))
    }

    // --- Nom propose ----------------------------------------------------------

    @Test
    fun `le nom propose est lisible`() {
        // « export_2024_final_v3.csv » dans la liste des matieres ne dit rien.
        assertEquals("Portugais a1", FormatImportEngine.nomPropose("portugais_a1.txt"))
        assertEquals("Mes cartes", FormatImportEngine.nomPropose("/chemin/mes-cartes.csv"))
        assertEquals("Module importé", FormatImportEngine.nomPropose(".txt"))
        assertEquals("Module importé", FormatImportEngine.nomPropose(""))
    }
}
