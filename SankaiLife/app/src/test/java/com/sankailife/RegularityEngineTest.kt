package com.sankailife

import com.sankailife.core.data.db.entities.DayRecordEntity
import com.sankailife.core.domain.engine.RegularityEngine
import com.sankailife.core.domain.engine.RegularityEngine.Statut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de la logique de série et de régularité.
 *
 * C'est le seul moteur dont un défaut corromprait silencieusement la
 * progression de l'utilisateur : une série perdue à tort ou un bouclier
 * consommé en double ne provoquent aucune erreur, juste un chiffre faux.
 */
class RegularityEngineTest {

    private val aujourdhui: LocalDate = LocalDate.of(2026, 3, 15)
    private fun jour(decalage: Long) = aujourdhui.minusDays(decalage).toString()

    // --- evaluerRetour ----------------------------------------------------

    @Test
    fun `premier lancement demarre une serie de 1`() {
        val r = RegularityEngine.evaluerRetour("", 0, 1, aujourdhui)
        assertEquals(1, r.nouvelleSerie)
        assertFalse(r.serieCassee)
        assertEquals(0, r.boucliersUtilises)
    }

    @Test
    fun `revenir le lendemain incremente la serie sans consommer de bouclier`() {
        val r = RegularityEngine.evaluerRetour(jour(1), 5, 2, aujourdhui)
        assertEquals(6, r.nouvelleSerie)
        assertEquals(0, r.boucliersUtilises)
        assertEquals(2, r.boucliersRestants)
    }

    @Test
    fun `revenir le meme jour ne change rien`() {
        val r = RegularityEngine.evaluerRetour(jour(0), 5, 2, aujourdhui)
        assertEquals(5, r.nouvelleSerie)
        assertEquals(0, r.boucliersUtilises)
    }

    @Test
    fun `un jour manque est absorbe par un bouclier et la serie continue`() {
        // Dernière visite avant-hier : une journée manquée.
        val r = RegularityEngine.evaluerRetour(jour(2), 10, 1, aujourdhui)
        assertFalse("la série ne doit pas casser", r.serieCassee)
        assertEquals(11, r.nouvelleSerie)
        assertEquals(1, r.boucliersUtilises)
        assertEquals(0, r.boucliersRestants)
    }

    @Test
    fun `sans bouclier la serie casse mais repart a 1`() {
        val r = RegularityEngine.evaluerRetour(jour(2), 10, 0, aujourdhui)
        assertTrue(r.serieCassee)
        assertEquals(1, r.nouvelleSerie)
        assertEquals(0, r.boucliersUtilises)
    }

    @Test
    fun `absence longue consomme tous les boucliers sans passer en negatif`() {
        // Cinq jours manqués, deux boucliers seulement.
        val r = RegularityEngine.evaluerRetour(jour(6), 20, 2, aujourdhui)
        assertTrue(r.serieCassee)
        assertEquals(1, r.nouvelleSerie)
        assertEquals(2, r.boucliersUtilises)
        assertEquals(0, r.boucliersRestants)
        assertEquals(5, r.absenceJours)
    }

    @Test
    fun `date illisible ne fait pas planter et repart proprement`() {
        val r = RegularityEngine.evaluerRetour("pas-une-date", 7, 1, aujourdhui)
        assertEquals(1, r.nouvelleSerie)
        assertFalse(r.serieCassee)
    }

    // --- boucliers gagnes -------------------------------------------------

    @Test
    fun `un bouclier est gagne tous les sept jours`() {
        assertEquals(1, RegularityEngine.boucliersGagnes(7, 0))
        assertEquals(1, RegularityEngine.boucliersGagnes(14, 1))
        assertEquals(0, RegularityEngine.boucliersGagnes(8, 0))
    }

    @Test
    fun `le plafond de boucliers est respecte`() {
        assertEquals(0, RegularityEngine.boucliersGagnes(7, RegularityEngine.MAX_BOUCLIERS))
    }

    // --- regularite -------------------------------------------------------

    @Test
    fun `sept jours tenus donnent cent pour cent`() {
        val records = (0..6).map { DayRecordEntity(jour(it.toLong()), Statut.SUCCES) }
        assertEquals(100, RegularityEngine.pourcentage(records, 7).let {
            RegularityEngine.regularite(records, 7, aujourdhui).times(100).toInt()
        })
    }

    @Test
    fun `un jour manque sur sept ne fait pas tomber la regularite a zero`() {
        val records = (0..6).map {
            DayRecordEntity(jour(it.toLong()), if (it == 3) Statut.MANQUE else Statut.SUCCES)
        }
        val pct = (RegularityEngine.regularite(records, 7, aujourdhui) * 100).toInt()
        assertTrue("attendu autour de 85 %, obtenu $pct", pct in 80..90)
    }

    @Test
    fun `une journee en pause ne penalise pas la regularite`() {
        // Six jours réussis, un en pause : la pause sort du dénominateur,
        // le score doit rester à 100 % et non tomber à 86 %.
        val records = (0..6).map {
            DayRecordEntity(jour(it.toLong()), if (it == 2) Statut.PAUSE else Statut.SUCCES)
        }
        val pct = (RegularityEngine.regularite(records, 7, aujourdhui) * 100).toInt()
        assertEquals(100, pct)
    }

    @Test
    fun `un jour protege par bouclier compte comme tenu`() {
        val records = (0..6).map {
            DayRecordEntity(jour(it.toLong()), if (it == 1) Statut.PROTEGE else Statut.SUCCES)
        }
        val pct = (RegularityEngine.regularite(records, 7, aujourdhui) * 100).toInt()
        assertEquals(100, pct)
    }

    @Test
    fun `aucun historique donne zero sans planter`() {
        assertEquals(0, RegularityEngine.pourcentage(emptyList(), 30))
    }

    @Test
    fun `les jours hors fenetre sont ignores`() {
        // Journées vieilles de 40 jours : hors d'une fenêtre de 7.
        val records = (40..46).map { DayRecordEntity(jour(it.toLong()), Statut.SUCCES) }
        assertEquals(0, (RegularityEngine.regularite(records, 7, aujourdhui) * 100).toInt())
    }
}
