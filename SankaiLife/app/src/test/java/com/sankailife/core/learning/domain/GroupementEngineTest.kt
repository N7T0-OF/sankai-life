package com.sankailife.core.learning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupementEngineTest {

    private fun module(
        id: Long,
        nom: String,
        collection: String = "",
        niveau: String = "",
        cartes: Int = 100,
        progression: Float = 0f
    ) = GroupementEngine.Module(
        profileId = id, nom = nom, collection = collection,
        niveau = niveau, cartes = cartes, progression = progression
    )

    private fun portugais(vararg progressions: Float): List<GroupementEngine.Module> {
        val niveaux = listOf(
            "A1" to 189, "A2" to 161, "B1" to 133, "B2" to 115, "C1" to 111, "C2" to 88
        )
        return niveaux.mapIndexed { i, (niveau, cartes) ->
            module(
                id = i.toLong(),
                nom = "Portugais $niveau — Niveau $niveau",
                collection = "portugais",
                niveau = niveau,
                cartes = cartes,
                progression = progressions.getOrElse(i) { 0f }
            )
        }
    }

    // --- Regroupement ---------------------------------------------------------

    @Test
    fun `rien a grouper ne fait pas planter`() {
        assertTrue(GroupementEngine.grouper(emptyList()).isEmpty())
        assertTrue(GroupementEngine.ouvertsParDefaut(emptyList()).isEmpty())
    }

    @Test
    fun `six niveaux donnent un seul groupe`() {
        // C'est tout l'objet : six lignes plates devenaient illisibles.
        val groupes = GroupementEngine.grouper(portugais())
        assertEquals(1, groupes.size)
        assertEquals(6, groupes.single().modules.size)
        assertTrue(groupes.single().estParcours)
    }

    @Test
    fun `un module seul reste un groupe d'un seul element`() {
        // Meme forme pour l'ecran, et un module qui rejoint un parcours plus
        // tard ne change pas de nature.
        val groupes = GroupementEngine.grouper(listOf(module(1, "Raccourcis Blender")))
        assertEquals(1, groupes.size)
        assertFalse(groupes.single().estParcours)
    }

    @Test
    fun `les parcours passent devant les modules seuls`() {
        val groupes = GroupementEngine.grouper(
            listOf(module(9, "Aaa liste de courses")) + portugais()
        )
        assertTrue(groupes.first().estParcours)
        assertFalse(groupes.last().estParcours)
    }

    @Test
    fun `les niveaux sont ranges dans l'ordre europeen`() {
        // Quelqu'un qui installe le B1 avant le A1 doit voir A1 en premier.
        val desordre = portugais().shuffled()
        val niveaux = GroupementEngine.grouper(desordre).single().modules.map { it.niveau }
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), niveaux)
    }

    @Test
    fun `un niveau inconnu ne casse pas l'ordre et finit a la fin`() {
        val avecIntrus = portugais() + module(
            99, "Portugais — Annexes", collection = "portugais", niveau = ""
        )
        val niveaux = GroupementEngine.grouper(avecIntrus).single().modules.map { it.niveau }
        assertEquals("A1", niveaux.first())
        assertEquals("", niveaux.last())
    }

    // --- Titre ----------------------------------------------------------------

    @Test
    fun `le titre est le prefixe commun des niveaux`() {
        // L'identifiant du parcours est technique ; inventer un libelle
        // produirait un titre qui ne ressemble a aucun module affiche dessous.
        assertEquals("Portugais", GroupementEngine.grouper(portugais()).single().titre)
    }

    @Test
    fun `sans prefixe commun on garde le nom du premier`() {
        val disparates = listOf(
            module(1, "Alpha", collection = "x", niveau = "A1"),
            module(2, "Beta", collection = "x", niveau = "A2")
        )
        assertEquals("Alpha", GroupementEngine.grouper(disparates).single().titre)
    }

    @Test
    fun `le titre ne se termine pas par un tiret orphelin`() {
        val modules = listOf(
            module(1, "Japonais — Kanji", collection = "jp", niveau = "A1"),
            module(2, "Japonais — Kana", collection = "jp", niveau = "A2")
        )
        val titre = GroupementEngine.grouper(modules).single().titre
        assertEquals("Japonais", titre)
    }

    // --- Progression ----------------------------------------------------------

    @Test
    fun `la progression est ponderee par le nombre de cartes`() {
        // Une moyenne simple donnerait le meme poids au C2 qu'au A1, alors que
        // l'un compte 88 cartes et l'autre 189 : terminer le plus petit ferait
        // bondir le pourcentage sans qu'on ait appris davantage.
        val groupe = GroupementEngine.grouper(
            portugais(0f, 0f, 0f, 0f, 0f, 1f)   // seul le C2, le plus petit
        ).single()
        val moyenneSimple = 1f / 6f
        assertTrue(
            "Progression ${groupe.progression} : le petit niveau pese trop",
            groupe.progression < moyenneSimple
        )
        assertEquals(88f / 797f, groupe.progression, 0.001f)
    }

    @Test
    fun `tout maitrise donne cent pour cent`() {
        val groupe = GroupementEngine.grouper(portugais(1f, 1f, 1f, 1f, 1f, 1f)).single()
        assertEquals(1f, groupe.progression, 0.0001f)
    }

    @Test
    fun `un groupe sans carte ne divise pas par zero`() {
        val vide = listOf(module(1, "Vide", collection = "x", cartes = 0))
        assertEquals(0f, GroupementEngine.grouper(vide).single().progression, 0.0001f)
    }

    // --- Niveau actuel --------------------------------------------------------

    @Test
    fun `le niveau actuel est le premier non acquis`() {
        val groupe = GroupementEngine.grouper(portugais(1f, 0.3f)).single()
        assertEquals("A2", groupe.niveauActuel?.niveau)
    }

    @Test
    fun `tout acquis laisse le dernier niveau comme actuel`() {
        // « Tu es au C2 » est plus juste que « aucun niveau en cours ».
        val groupe = GroupementEngine.grouper(portugais(1f, 1f, 1f, 1f, 1f, 1f)).single()
        assertEquals("C2", groupe.niveauActuel?.niveau)
    }

    @Test
    fun `le resume dit ce qui compte`() {
        val resume = GroupementEngine.grouper(portugais(1f, 0.2f)).single().resume
        assertTrue(resume.contains("6 niveaux"))
        assertTrue(resume.contains("797 cartes"))
        assertTrue(resume.contains("A2"))
    }

    // --- Ouverture par defaut -------------------------------------------------

    @Test
    fun `un seul parcours s'ouvre a l'arrivee`() {
        // Tout ouvrir redonne la liste plate qu'on vient de corriger ; tout
        // fermer oblige a un geste avant de voir quoi que ce soit.
        val groupes = GroupementEngine.grouper(portugais(0.5f))
        assertEquals(setOf("portugais"), GroupementEngine.ouvertsParDefaut(groupes))
    }

    @Test
    fun `c'est le parcours commence qui s'ouvre`() {
        val deux = portugais(0.4f) + listOf(
            module(20, "Anglais A1", collection = "anglais", niveau = "A1"),
            module(21, "Anglais A2", collection = "anglais", niveau = "A2")
        )
        val groupes = GroupementEngine.grouper(deux)
        assertEquals(setOf("portugais"), GroupementEngine.ouvertsParDefaut(groupes))
    }

    @Test
    fun `sans parcours rien ne s'ouvre`() {
        val groupes = GroupementEngine.grouper(listOf(module(1, "Blender")))
        assertTrue(GroupementEngine.ouvertsParDefaut(groupes).isEmpty())
    }
}
