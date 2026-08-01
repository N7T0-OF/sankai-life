package com.sankailife.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MemorisationEngineTest {

    @Test
    fun `une base vide ne divise pas par zero`() {
        assertEquals(0f, MemorisationEngine.partMaitrisee(0, 0), 0.001f)
        assertEquals(0, MemorisationEngine.Etat().jamaisVues)
    }

    @Test
    fun `la part maitrisee reste entre zero et un`() {
        assertEquals(0.5f, MemorisationEngine.partMaitrisee(10, 5), 0.001f)
        // Incohérence en base : plus de maîtrisées que de cartes.
        assertEquals(1f, MemorisationEngine.partMaitrisee(10, 40), 0.001f)
    }

    @Test
    fun `aucun taux n'est publie avant assez de revisions`() {
        // Répondre juste à sa première carte ne fait pas 100 % de réussite ;
        // l'afficher féliciterait quelqu'un qui n'a encore rien appris.
        assertNull(MemorisationEngine.tauxReussite(revisions = 1, reussites = 1))
        assertNull(
            MemorisationEngine.tauxReussite(
                revisions = MemorisationEngine.REVISIONS_POUR_UN_TAUX - 1,
                reussites = 0
            )
        )
        assertNotNull(
            MemorisationEngine.tauxReussite(
                revisions = MemorisationEngine.REVISIONS_POUR_UN_TAUX,
                reussites = 10
            )
        )
    }

    @Test
    fun `le taux publie est correct et borne`() {
        assertEquals(0.5f, MemorisationEngine.tauxReussite(40, 20)!!, 0.001f)
        // Plus de réussites que de révisions : on plafonne au lieu d'annoncer
        // un taux supérieur à 100 %.
        assertEquals(1f, MemorisationEngine.tauxReussite(40, 80)!!, 0.001f)
    }

    @Test
    fun `le pourcentage ne ment pas par arrondi`() {
        assertEquals("0 %", MemorisationEngine.pourcentage(0f))
        assertEquals("50 %", MemorisationEngine.pourcentage(0.5f))
        assertEquals("100 %", MemorisationEngine.pourcentage(1f))
        // 99,9 % n'est pas 100 % : quelqu'un qui rate encore une carte ne doit
        // pas lire qu'il a tout maîtrisé.
        assertEquals("99 %", MemorisationEngine.pourcentage(0.999f))
    }

    @Test
    fun `les cartes jamais vues se deduisent du total`() {
        val etat = MemorisationEngine.Etat(total = 30, entamees = 12)
        assertEquals(18, etat.jamaisVues)
    }

    @Test
    fun `jamais vues ne devient jamais negatif`() {
        val etat = MemorisationEngine.Etat(total = 5, entamees = 9)
        assertEquals(0, etat.jamaisVues)
    }

    @Test
    fun `le resume distingue les etapes`() {
        assertEquals(
            "Aucune phrase enregistrée pour l'instant.",
            MemorisationEngine.resume(MemorisationEngine.Etat())
        )
        assertEquals(
            "12 phrases prêtes, aucune encore révisée.",
            MemorisationEngine.resume(MemorisationEngine.Etat(total = 12, revisions = 0))
        )
        assertEquals(
            "3 carte(s) à réviser aujourd'hui.",
            MemorisationEngine.resume(
                MemorisationEngine.Etat(total = 12, revisions = 40, dues = 3)
            )
        )
    }

    @Test
    fun `tout maitrise se dit autrement que rien a faire maintenant`() {
        val toutSu = MemorisationEngine.Etat(
            total = 12, maitrisees = 12, dues = 0, revisions = 60, reussites = 55
        )
        val enCours = MemorisationEngine.Etat(
            total = 12, maitrisees = 4, dues = 0, revisions = 60, reussites = 40
        )
        assertEquals(
            "Tout est en dernière boîte. Rien à réviser aujourd'hui.",
            MemorisationEngine.resume(toutSu)
        )
        assertEquals(
            "Rien à réviser dans l'immédiat — reviens plus tard.",
            MemorisationEngine.resume(enCours)
        )
    }

    @Test
    fun `la boite maitrisee est la derniere, pas une de plus`() {
        // Les boîtes vont de 0 à NOMBRE_BOITES − 1. Compter la maîtrise à
        // NOMBRE_BOITES ne trouverait jamais aucune carte.
        assertEquals(FlashcardEngine.NOMBRE_BOITES - 1, MemorisationEngine.BOITE_MAITRISEE)
        assertEquals(
            MemorisationEngine.BOITE_MAITRISEE,
            FlashcardEngine.boiteSuivante(
                MemorisationEngine.BOITE_MAITRISEE,
                FlashcardEngine.Jugement.FACILE
            )
        )
    }
}
