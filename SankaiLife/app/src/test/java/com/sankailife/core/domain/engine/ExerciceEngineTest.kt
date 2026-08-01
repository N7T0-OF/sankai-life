package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExerciceEngineTest {

    private fun carte(
        id: Long,
        recto: String,
        verso: String?,
        box: Int,
        moduleId: Long = 0L
    ) = FlashcardEngine.Carte(id, recto, verso, box, moduleId = moduleId)

    private val reservoir = listOf(
        carte(2, "Capitale de la France", "Paris", 0),
        carte(3, "Capitale de l'Italie", "Rome", 0),
        carte(4, "Capitale de l'Espagne", "Madrid", 0),
        carte(5, "Capitale du Portugal", "Lisbonne", 0)
    )

    private val aleatoire = Random(42)

    @Test
    fun `une carte fraiche donne un QCM`() {
        val c = carte(1, "Capitale du Japon", "Tokyo", 0)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)

        assertTrue(ex is ExerciceEngine.Exercice.Reconnaissance)
        ex as ExerciceEngine.Exercice.Reconnaissance
        assertEquals(4, ex.options.size)
        assertTrue(ex.options.contains("Tokyo"))
    }

    @Test
    fun `une carte acquise demande d'ecrire`() {
        // La progression reconnaissance → production est le cœur du système :
        // savoir choisir n'est pas savoir restituer.
        val c = carte(1, "Capitale du Japon", "Tokyo", 4)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)
        assertTrue(ex is ExerciceEngine.Exercice.Saisie)
    }

    @Test
    fun `sans leurres suffisants on bascule sur la saisie`() {
        // Un QCM à deux options se devine à pile ou face : mieux vaut écrire.
        val c = carte(1, "Capitale du Japon", "Tokyo", 0)
        val ex = ExerciceEngine.construire(c, reservoir.take(1), aleatoire)
        assertTrue(ex is ExerciceEngine.Exercice.Saisie)
    }

    @Test
    fun `une carte d'une seule face devient un remise en ordre`() {
        val c = carte(1, "Continue meme si c'est lent", null, 0)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)

        assertTrue(ex is ExerciceEngine.Exercice.Ordre)
        ex as ExerciceEngine.Exercice.Ordre
        assertEquals(5, ex.morceaux.size)
    }

    @Test
    fun `une carte d'une seule face trop courte reste une carte memoire`() {
        val c = carte(1, "Respire", null, 0)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)
        assertTrue(ex is ExerciceEngine.Exercice.Memoire)
    }

    @Test
    fun `le texte a trous retire un seul mot de la reponse`() {
        val c = carte(1, "Phrase", "le chat dort sur le tapis", 2)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)

        assertTrue(ex is ExerciceEngine.Exercice.TexteATrous)
        ex as ExerciceEngine.Exercice.TexteATrous
        val reconstruit = listOf(ex.avant, ex.attendu, ex.apres)
            .filter { it.isNotBlank() }.joinToString(" ")
        assertEquals("le chat dort sur le tapis", reconstruit)
    }

    @Test
    fun `un leurre identique a la bonne reponse est ecarte`() {
        // Deux cartes différentes peuvent avoir la même réponse ; la proposer
        // deux fois rendrait le QCM insoluble.
        val doublons = reservoir + carte(9, "Autre question", "Tokyo", 0)
        val c = carte(1, "Capitale du Japon", "Tokyo", 0)
        val ex = ExerciceEngine.construire(c, doublons, aleatoire)

        ex as ExerciceEngine.Exercice.Reconnaissance
        assertEquals(1, ex.options.count { it == "Tokyo" })
    }

    @Test
    fun `les cartes d'autres modules ne deviennent jamais des leurres`() {
        val carte = carte(1, "Question A", "Réponse A", 0, moduleId = 10)
        val memeModule = listOf(
            carte(2, "Question B", "Réponse B", 0, moduleId = 10),
            carte(3, "Question C", "Réponse C", 0, moduleId = 10)
        )
        val autreModule = listOf(
            carte(4, "Animal", "Chat", 0, moduleId = 20),
            carte(5, "Couleur", "Bleu", 0, moduleId = 20),
            carte(6, "Nombre", "Trois", 0, moduleId = 20)
        )

        val exercice = ExerciceEngine.construire(
            carte,
            memeModule + autreModule,
            aleatoire
        )

        // Deux leurres du bon module ne suffisent pas : le moteur préfère une
        // saisie à un QCM rendu évident par trois réponses hors sujet.
        assertTrue(exercice is ExerciceEngine.Exercice.Saisie)
    }

    @Test
    fun `la correction accepte une faute de frappe en saisie`() {
        val c = carte(1, "Capitale du Portugal", "Lisbonne", 4)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)
        assertEquals(true, ExerciceEngine.corriger(ex, "Lisbone"))
        assertEquals(false, ExerciceEngine.corriger(ex, "Madrid"))
    }

    @Test
    fun `la remise en ordre se corrige sur la phrase reconstituee`() {
        val c = carte(1, "Le chat dort sur le tapis", null, 0)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)
        assertEquals(true, ExerciceEngine.corriger(ex, "le chat dort sur le tapis"))
        assertEquals(false, ExerciceEngine.corriger(ex, "le tapis dort sur le chat"))
    }

    @Test
    fun `une carte memoire ne se corrige pas`() {
        // null et false doivent rester distincts : compter une carte non
        // corrigeable comme une erreur la renverrait injustement en boîte 0.
        val c = carte(1, "Respire", null, 0)
        val ex = ExerciceEngine.construire(c, reservoir, aleatoire)
        assertNull(ExerciceEngine.corriger(ex, "peu importe"))
        assertNull(ExerciceEngine.reponseAttendue(ex))
    }

    @Test
    fun `chaque boite produit un exercice, sans exception`() {
        // Une boîte non couverte laisserait une carte sans exercice, donc une
        // session bloquée. Le test balaie tout l'intervalle.
        for (box in 0 until FlashcardEngine.NOMBRE_BOITES) {
            val c = carte(1, "Capitale du Japon", "Tokyo", box)
            ExerciceEngine.construire(c, reservoir, aleatoire)
        }
    }
}
