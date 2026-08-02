package com.sankailife.core.learning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademieEngineTest {

    private fun cartes(n: Int, unite: (Int) -> String? = { null }) =
        (0 until n).map {
            AcademieEngine.Carte(id = it.toLong(), ordre = it, uniteDeclaree = unite(it))
        }

    // --- Decoupage ------------------------------------------------------------

    @Test
    fun `un module vide ne donne aucune unite`() {
        assertTrue(AcademieEngine.decouper(emptyList()).isEmpty())
    }

    @Test
    fun `un deck sans structure est decoupe en paquets`() {
        // Le cas reel de tous les profils Memo existants : aucune hierarchie
        // declaree, juste des lignes dans l'ordre ou l'utilisateur les a
        // ecrites.
        val unites = AcademieEngine.decouper(cartes(25))
        assertEquals(3, unites.size)
        assertEquals(AcademieEngine.CARTES_PAR_UNITE, unites[0].taille)
        assertEquals(AcademieEngine.CARTES_PAR_UNITE, unites[1].taille)
        assertEquals(5, unites[2].taille)
    }

    @Test
    fun `aucune carte n'est perdue au decoupage`() {
        // Le defaut le plus grave possible ici : une carte qui n'apparait dans
        // aucune unite est une carte qu'on ne reverra jamais.
        val toutes = cartes(37)
        val vues = AcademieEngine.decouper(toutes).flatMap { it.cartes }.toSet()
        assertEquals(toutes.map { it.id }.toSet(), vues)
    }

    @Test
    fun `aucune unite n'est vide`() {
        // Un noeud sans carte est un noeud sur lequel on clique pour rien.
        listOf(1, 9, 10, 11, 30, 31).forEach { n ->
            assertTrue("$n cartes produit une unite vide",
                AcademieEngine.decouper(cartes(n)).all { it.taille > 0 })
        }
    }

    @Test
    fun `une structure declaree est respectee`() {
        val avecUnites = cartes(6) { i -> if (i < 4) "Salutations" else "Politesse" }
        val unites = AcademieEngine.decouper(avecUnites)
        assertEquals(2, unites.size)
        assertEquals("Salutations", unites[0].titre)
        assertEquals(4, unites[0].taille)
        assertEquals("Politesse", unites[1].titre)
        assertEquals(2, unites[1].taille)
    }

    @Test
    fun `les cartes mal etiquetees ne sont pas perdues`() {
        // Perdre du contenu parce qu'il est mal etiquete serait pire que
        // l'afficher en vrac.
        val melange = cartes(5) { i -> if (i < 3) "Salutations" else null }
        val unites = AcademieEngine.decouper(melange)
        assertEquals(2, unites.size)
        assertEquals("À classer", unites[1].titre)
        assertEquals(setOf(3L, 4L), unites[1].cartes.toSet())
    }

    @Test
    fun `les unites sont groupees en chapitres`() {
        val unites = AcademieEngine.decouper(cartes(70))
        assertEquals(0, unites[0].chapitre)
        assertEquals(0, unites[2].chapitre)
        assertEquals(1, unites[3].chapitre)
    }

    @Test
    fun `l'ordre des cartes est celui du contenu`() {
        // Melangees a l'entree, remises dans l'ordre de l'utilisateur.
        val desordre = cartes(12).shuffled()
        val unites = AcademieEngine.decouper(desordre)
        assertEquals((0L..9L).toList(), unites[0].cartes)
    }

    // --- Parcours -------------------------------------------------------------

    @Test
    fun `la premiere unite est toujours ouverte`() {
        // Sinon un module neuf n'aurait aucun point d'entree.
        val parcours = AcademieEngine.parcours(
            AcademieEngine.decouper(cartes(30)), maitrisees = emptySet()
        )
        assertEquals(AcademieEngine.Etat.ACTUELLE, parcours.first().etat)
    }

    @Test
    fun `les unites suivantes sont verrouillees au depart`() {
        val parcours = AcademieEngine.parcours(
            AcademieEngine.decouper(cartes(30)), maitrisees = emptySet()
        )
        assertTrue(parcours.drop(1).all { it.etat == AcademieEngine.Etat.VERROUILLEE })
    }

    @Test
    fun `commencer une unite ouvre la suivante`() {
        // La regle est volontairement souple : exiger la perfection avant de
        // laisser avancer transforme le parcours en mur.
        val unites = AcademieEngine.decouper(cartes(30))
        val parcours = AcademieEngine.parcours(
            unites, maitrisees = emptySet(), vues = setOf(0L)
        )
        assertEquals(AcademieEngine.Etat.DISPONIBLE, parcours[1].etat)
        assertEquals(AcademieEngine.Etat.VERROUILLEE, parcours[2].etat)
    }

    @Test
    fun `une unite suffisamment maitrisee est terminee`() {
        val unites = AcademieEngine.decouper(cartes(20))
        // 8 cartes sur 10 = 80 %, exactement le seuil.
        val parcours = AcademieEngine.parcours(unites, maitrisees = (0L..7L).toSet())
        assertEquals(AcademieEngine.Etat.TERMINEE, parcours[0].etat)
        assertEquals(AcademieEngine.Etat.ACTUELLE, parcours[1].etat)
    }

    @Test
    fun `la perfection n'est pas exigee`() {
        // Une carte recalcitrante ne doit pas bloquer tout le parcours : elle
        // reviendra de toute facon en revision.
        val unites = AcademieEngine.decouper(cartes(10))
        val parcours = AcademieEngine.parcours(unites, maitrisees = (0L..7L).toSet())
        assertEquals(AcademieEngine.Etat.TERMINEE, parcours.single().etat)
    }

    @Test
    fun `une seule unite est actuelle a la fois`() {
        // C'est ce qui permet a l'accueil de n'afficher qu'un bouton.
        val unites = AcademieEngine.decouper(cartes(50))
        val parcours = AcademieEngine.parcours(
            unites, maitrisees = (0L..9L).toSet(), vues = (0L..25L).toSet()
        )
        assertEquals(1, parcours.count { it.etat == AcademieEngine.Etat.ACTUELLE })
    }

    @Test
    fun `a continuer designe toujours une unite atteignable`() {
        val unites = AcademieEngine.decouper(cartes(40))
        val parcours = AcademieEngine.parcours(unites, maitrisees = (0L..9L).toSet())
        val suite = AcademieEngine.aContinuer(parcours)
        assertNotNull(suite)
        val noeud = parcours.first { it.unite.id == suite!!.id }
        assertTrue(noeud.etat != AcademieEngine.Etat.VERROUILLEE)
    }

    @Test
    fun `tout maitrise ne laisse rien a continuer`() {
        val unites = AcademieEngine.decouper(cartes(20))
        val parcours = AcademieEngine.parcours(unites, maitrisees = (0L..19L).toSet())
        assertTrue(parcours.all { it.etat == AcademieEngine.Etat.TERMINEE })
        assertNull(AcademieEngine.aContinuer(parcours))
    }

    @Test
    fun `un parcours vide ne fait pas planter`() {
        assertTrue(AcademieEngine.parcours(emptyList(), emptySet()).isEmpty())
        assertNull(AcademieEngine.aContinuer(emptyList()))
        assertEquals(0f, AcademieEngine.progression(emptyList()), 0.0001f)
    }

    // --- Progression ----------------------------------------------------------

    @Test
    fun `la progression pese les unites par leur taille`() {
        // Une derniere unite de 5 cartes ne vaut pas autant qu'une de 10 :
        // sinon terminer un petit reste afficherait un bond trompeur.
        val unites = AcademieEngine.decouper(cartes(15))
        val parcours = AcademieEngine.parcours(unites, maitrisees = (0L..9L).toSet())
        assertEquals(10f / 15f, AcademieEngine.progression(parcours), 0.001f)
    }

    @Test
    fun `la progression reste bornee`() {
        val unites = AcademieEngine.decouper(cartes(20))
        val p = AcademieEngine.parcours(unites, maitrisees = (0L..19L).toSet())
        assertEquals(1f, AcademieEngine.progression(p), 0.0001f)
    }
}
