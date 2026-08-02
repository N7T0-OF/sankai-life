package com.sankailife.core.learning.domain

import com.sankailife.core.learning.domain.SessionPlanEngine.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlanEngineTest {

    private fun cartes(
        n: Int,
        aVerso: Boolean = true,
        boite: Int = 1,
        motsVerso: Int = 4
    ) = (0 until n).map {
        SessionPlanEngine.Carte(
            id = it.toLong(), aVerso = aVerso, boite = boite, motsVerso = motsVerso
        )
    }

    private fun composer(
        cartes: List<SessionPlanEngine.Carte>,
        minutes: Int = 5,
        typesRecents: List<Type> = emptyList(),
        disponibles: Set<Type> = SessionPlanEngine.DISPONIBLES,
        graine: Long = 7L
    ) = SessionPlanEngine.composer(
        moduleId = 1L, uniteId = "u0", cartes = cartes,
        minutes = minutes, typesRecents = typesRecents,
        disponibles = disponibles, graine = graine
    )

    // --- Cas limites ----------------------------------------------------------

    @Test
    fun `sans carte il n'y a pas de session`() {
        val plan = composer(emptyList())
        assertTrue(plan.vide)
        assertEquals(0, plan.minutesEstimees)
    }

    @Test
    fun `sans type disponible il n'y a pas de session`() {
        // Le cas d'un appareil sans voix installee et sans micro : mieux vaut
        // rien proposer que lancer une session vide.
        assertTrue(composer(cartes(10), disponibles = emptySet()).vide)
    }

    @Test
    fun `un type declare mais non construit n'est jamais propose`() {
        // La lecon est recente : offrir un exercice sans effet, c'est vendre un
        // objet qui ne fait rien. Ce test verrouille le couple.
        val plan = composer(cartes(10), disponibles = Type.entries.toSet())
        assertTrue(
            "Un exercice non construit a ete propose",
            plan.exercices.all { it.type in SessionPlanEngine.DISPONIBLES }
        )
    }

    @Test
    fun `une carte sans verso ne recoit que ce qu'elle permet`() {
        // Un QCM sur une carte sans reponse n'a rien a proposer comme leurre.
        val plan = composer(cartes(6, aVerso = false))
        assertFalse(plan.vide)
        assertTrue(
            "Un exercice exige un verso absent",
            plan.exercices.all { !it.type.besoinVerso }
        )
    }

    // --- Composition ----------------------------------------------------------

    @Test
    fun `la session tient a peu pres la duree annoncee`() {
        val plan = composer(cartes(40), minutes = 5)
        val secondes = plan.exercices.sumOf { it.type.secondes }
        // Elle remplit le temps sans le depasser : une session qui s'arrete a
        // deux minutes quand on en a annonce cinq est aussi trompeuse.
        assertTrue("Session de $secondes s pour 5 min annoncees", secondes in 260..300)
        assertEquals(5, plan.minutesEstimees)
    }

    @Test
    fun `une session plus longue contient plus d'exercices`() {
        val courte = composer(cartes(60), minutes = 3)
        val longue = composer(cartes(60), minutes = 10)
        assertTrue(longue.exercices.size > courte.exercices.size)
    }

    @Test
    fun `la session ne depasse jamais le plafond`() {
        // Au-dela, c'est une corvee quel que soit le temps annonce.
        val plan = composer(cartes(200), minutes = 60)
        assertTrue(plan.exercices.size <= SessionPlanEngine.EXERCICES_MAX)
    }

    @Test
    fun `deux exercices identiques ne se suivent jamais`() {
        // Se remarque immediatement et donne l'impression d'un bug.
        val plan = composer(cartes(40), minutes = 10)
        plan.exercices.zipWithNext().forEach { (a, b) ->
            assertNotEquals("Repetition de ${a.type}", a.type, b.type)
        }
    }

    @Test
    fun `la session contient toujours du rappel actif`() {
        // Une session entierement en reconnaissance donne l'illusion de savoir.
        val plan = composer(cartes(20))
        assertTrue(plan.exercices.any { it.type.production })
    }

    @Test
    fun `la session ne se termine jamais sur un exercice qu'on peut rater`() {
        // Quitter sur un echec laisse le souvenir de l'echec. Le dernier
        // exercice est donc choisi ou auto-evalue, jamais corrige.
        listOf(3, 5, 8, 12).forEach { minutes ->
            val dernier = composer(cartes(30), minutes = minutes).exercices.last().type
            assertTrue(
                "Session de $minutes min terminee sur $dernier",
                !dernier.production || dernier.autoEvalue
            )
        }
    }

    @Test
    fun `la session ne depasse jamais la duree annoncee`() {
        // Annoncer cinq minutes et en livrer cinq et demie est le genre de
        // petit mensonge qui fait qu'on ne croit plus les durees affichees.
        listOf(1, 3, 5, 10, 15).forEach { minutes ->
            val plan = composer(cartes(60), minutes = minutes)
            val secondes = plan.exercices.sumOf { it.type.secondes }
            assertTrue("$secondes s pour $minutes min", secondes <= minutes * 60)
            assertTrue(plan.minutesEstimees <= minutes)
        }
    }

    @Test
    fun `une unite d'une seule carte ne la repete pas indefiniment`() {
        // Defaut trouve en ecrivant ce test : le budget de temps etait respecte
        // a la lettre et la session proposait la meme carte quatre fois.
        val plan = composer(cartes(1), minutes = 5)
        assertTrue("Session vide", plan.exercices.isNotEmpty())
        assertTrue(
            "La carte revient ${plan.exercices.size} fois",
            plan.exercices.size <= SessionPlanEngine.PASSAGES_PAR_CARTE
        )
    }

    @Test
    fun `aucune carte ne revient plus que le plafond de passages`() {
        val plan = composer(cartes(4), minutes = 20)
        plan.exercices.groupingBy { it.carteId }.eachCount().forEach { (id, n) ->
            assertTrue("Carte $id vue $n fois", n <= SessionPlanEngine.PASSAGES_PAR_CARTE)
        }
    }

    @Test
    fun `chaque type disponible sait se construire`() {
        // « Disponible » ne veut rien dire sans une forme qui sache l'afficher.
        SessionPlanEngine.DISPONIBLES.forEach { type ->
            assertTrue(
                "$type est propose mais rien ne sait le construire",
                SessionPlanEngine.forme(type) != null ||
                    type in SessionPlanEngine.PAR_ASSOCIATION
            )
        }
    }

    @Test
    fun `aucun type constructible n'est oublie`() {
        // L'erreur inverse, et je l'ai faite : le texte a trous et la phrase a
        // reconstruire fonctionnaient depuis longtemps et n'etaient jamais
        // programmes, parce que DISPONIBLES n'en declarait que trois.
        Type.entries.filter {
            SessionPlanEngine.forme(it) != null || it in SessionPlanEngine.PAR_ASSOCIATION
        }.forEach { type ->
            assertTrue(
                "$type sait se construire mais n'est jamais propose",
                type in SessionPlanEngine.DISPONIBLES
            )
        }
    }

    @Test
    fun `une carte trop courte ne recoit pas un exercice qui exige des mots`() {
        // Sinon la session annonce « 2 texte a trous » et affiche deux saisies.
        val plan = composer(cartes(10, motsVerso = 1), minutes = 6)
        assertTrue(
            "Un exercice exige plus de mots que la carte n'en a",
            plan.exercices.all { it.type.motsVersoMin <= 1 }
        )
    }

    @Test
    fun `l'association n'est pas proposee sans assez de paires`() {
        // Elle se resoudrait par elimination des la deuxieme reponse.
        val plan = composer(cartes(2), minutes = 6)
        assertTrue(
            "Association proposee avec deux cartes seulement",
            plan.exercices.none { it.type == Type.MATCHING }
        )
    }

    @Test
    fun `l'association est proposee quand l'unite s'y prete`() {
        val plan = composer(cartes(12), minutes = 12)
        assertTrue(
            "Association jamais proposee malgre douze cartes",
            plan.exercices.any { it.type == Type.MATCHING }
        )
    }

    // --- Priorites ------------------------------------------------------------

    @Test
    fun `les erreurs passent devant tout le reste`() {
        val melange = listOf(
            SessionPlanEngine.Carte(1L, aVerso = true, boite = 4),
            SessionPlanEngine.Carte(2L, aVerso = true, boite = 3),
            SessionPlanEngine.Carte(3L, aVerso = true, boite = 2, enErreur = true)
        )
        assertEquals(3L, composer(melange, minutes = 1).exercices.first().carteId)
    }

    @Test
    fun `les cartes dues passent devant les cartes fraiches`() {
        val melange = listOf(
            SessionPlanEngine.Carte(1L, aVerso = true, boite = 1),
            SessionPlanEngine.Carte(2L, aVerso = true, boite = 3, due = true)
        )
        assertEquals(2L, composer(melange, minutes = 1).exercices.first().carteId)
    }

    @Test
    fun `les cartes fragiles passent avant les cartes acquises`() {
        val melange = listOf(
            SessionPlanEngine.Carte(1L, aVerso = true, boite = 4),
            SessionPlanEngine.Carte(2L, aVerso = true, boite = 0)
        )
        assertEquals(2L, composer(melange, minutes = 1).exercices.first().carteId)
    }

    @Test
    fun `une carte jamais reussie est d'abord reconnue`() {
        // Demander d'ecrire un mot vu une fois ne mesure rien.
        val plan = composer(cartes(8, boite = 0), minutes = 2)
        assertFalse(
            "Une carte en decouverte a ete mise en production d'emblee",
            plan.exercices.first().type == Type.TYPING
        )
    }

    @Test
    fun `toutes les cartes proposees viennent bien de l'unite`() {
        val donnees = cartes(12)
        val ids = donnees.map { it.id }.toSet()
        assertTrue(composer(donnees, minutes = 10).exercices.all { it.carteId in ids })
    }

    // --- Variete --------------------------------------------------------------

    @Test
    fun `un type vu recemment est depriorise`() {
        val sansHistorique = composer(cartes(20), minutes = 5)
        val avecHistorique = composer(
            cartes(20), minutes = 5, typesRecents = listOf(Type.MULTIPLE_CHOICE)
        )
        val partSans = sansHistorique.exercices.count { it.type == Type.MULTIPLE_CHOICE }
        val partAvec = avecHistorique.exercices.count { it.type == Type.MULTIPLE_CHOICE }
        assertTrue("Le type recent n'a pas ete depriorise", partAvec <= partSans)
    }

    @Test
    fun `un type banni ne rend pas la session vide`() {
        // Bannir plutot que deprioriser viderait le choix quand peu de types
        // sont disponibles.
        val plan = composer(
            cartes(20), typesRecents = SessionPlanEngine.DISPONIBLES.toList()
        )
        assertFalse(plan.vide)
    }

    @Test
    fun `la session melange plusieurs types`() {
        val plan = composer(cartes(30), minutes = 8)
        assertTrue(
            "Un seul type sur toute la session",
            plan.exercices.map { it.type }.toSet().size > 1
        )
    }

    // --- Determinisme ---------------------------------------------------------

    @Test
    fun `deux appels identiques donnent la meme session`() {
        // Sans cela, la moindre recomposition redistribuerait les exercices
        // sous les doigts de l'apprenant.
        val donnees = cartes(20)
        assertEquals(
            composer(donnees, graine = 42L).exercices,
            composer(donnees, graine = 42L).exercices
        )
    }

    @Test
    fun `deux graines differentes donnent des sessions differentes`() {
        val donnees = cartes(30)
        assertNotEquals(
            composer(donnees, minutes = 10, graine = 1L).exercices,
            composer(donnees, minutes = 10, graine = 999L).exercices
        )
    }

    // --- Resume ---------------------------------------------------------------

    @Test
    fun `le resume annonce ce qui va se passer`() {
        val resume = SessionPlanEngine.resume(composer(cartes(20), minutes = 5))
        assertTrue("Resume vide : $resume", resume.isNotBlank())
        assertTrue("Le resume ne compte rien : $resume", resume.any { it.isDigit() })
    }

    @Test
    fun `un plan vide le dit`() {
        assertEquals(
            "Rien à réviser pour l'instant.",
            SessionPlanEngine.resume(composer(emptyList()))
        )
    }
}
