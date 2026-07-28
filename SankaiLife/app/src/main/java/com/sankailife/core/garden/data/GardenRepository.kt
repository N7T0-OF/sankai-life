package com.sankailife.core.garden.data

import android.os.SystemClock
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.domain.*
import java.time.Instant
import java.time.LocalDate

/**
 * Point d'entrée unique du jardin.
 *
 * Toute la logique temporelle passe par ici : les écrans ne calculent jamais
 * de croissance eux-mêmes, ils affichent un état déjà résolu.
 */
class GardenRepository(
    private val db: SankaiDatabase,
    private val userRepo: UserRepository,
    private val clock: GardenClock = SystemGardenClock()
) {
    private val dao = db.gardenDao()

    /** Nombre de parcelles du prototype. */
    private val nombreParcelles = 9

    val etatFlow = dao.observerEtat()
    val parcellesFlow = dao.observerParcelles()
    val culturesFlow = dao.observerCultures()

    /**
     * Prépare le jardin au premier lancement.
     * Quatre parcelles cultivables, cinq à débloquer — assez pour jouer tout
     * de suite, assez peu pour donner envie d'agrandir.
     */
    suspend fun initialiser() {
        if (dao.etat() == null) {
            dao.sauverEtat(GardenStateEntity(jourPlafond = clock.currentDayId()))
        }
        if (dao.parcelles().isEmpty()) {
            dao.sauverParcelles(
                (0 until nombreParcelles).map { index ->
                    when {
                        index < 4 -> GardenPlotEntity(index, PlotState.EMPTY.name, "terre", 1)
                        index < 6 -> GardenPlotEntity(index, PlotState.UNCLEARED.name, "terre", 1)
                        else -> GardenPlotEntity(index, PlotState.LOCKED.name, "terre", index - 3)
                    }
                }
            )
        }
    }

    /**
     * Applique le temps écoulé depuis la dernière ouverture.
     *
     * @return le verdict de l'horloge, pour qu'un écran puisse expliquer une
     *         croissance suspendue au lieu de laisser le joueur deviner.
     */
    suspend fun ouvrirJardin(): TrustedTimeEngine.Verdict {
        initialiser()
        val etat = dao.etat() ?: return TrustedTimeEngine.Verdict.COHERENT

        val heureMurale = clock.now().toEpochMilli()
        val elapsed = clock.elapsedRealtimeMillis()

        val resultat = TrustedTimeEngine.evaluer(
            TrustedTimeState(etat.derniereHeureMurale, etat.dernierElapsedRealtime),
            heureMurale, elapsed
        )

        if (resultat.minutesRetenues > 0) {
            appliquerCroissance(resultat.minutesRetenues, heureMurale)
        }

        // Le plafond d'eau se remet à zéro au changement de jour.
        val jour = clock.currentDayId()
        val remise = jour != etat.jourPlafond

        dao.sauverEtat(
            etat.copy(
                derniereHeureMurale = heureMurale,
                dernierElapsedRealtime = elapsed,
                jourPlafond = jour,
                eauGagneeAujourdhui = if (remise) 0 else etat.eauGagneeAujourdhui
            )
        )
        return resultat.verdict
    }

    private suspend fun appliquerCroissance(minutes: Long, maintenant: Long) {
        for (culture in dao.culturesEnCours()) {
            val minutesDepuisArrosage = (maintenant - culture.dernierArrosageMillis) / 60_000
            // La part arrosée est bornée par la durée d'effet d'un arrosage.
            val minutesArrosees = (DUREE_ARROSAGE_MINUTES - minutesDepuisArrosage + minutes)
                .coerceIn(0L, minutes)

            val acquises = CropGrowthEngine.minutesAcquises(
                minutesEcoulees = minutes,
                minutesArrosees = minutesArrosees
            )
            dao.majCulture(culture.copy(minutesCumulees = culture.minutesCumulees + acquises))
        }
    }

    // --- Actions du joueur ------------------------------------------------

    /** Retire les pierres d'une parcelle encombrée. */
    suspend fun nettoyer(plotId: Int): Boolean {
        val parcelle = dao.parcelle(plotId) ?: return false
        if (parcelle.etat != PlotState.UNCLEARED.name) return false
        if (!userRepo.spendCoins(COUT_NETTOYAGE)) return false
        dao.majEtatParcelle(plotId, PlotState.EMPTY.name)
        return true
    }

    /**
     * Plante une graine. Le prix est débité au moment de la plantation :
     * pas d'inventaire de graines dans le prototype, une transaction de moins
     * à réconcilier.
     */
    suspend fun planter(plotId: Int, seedId: String): Boolean {
        val parcelle = dao.parcelle(plotId) ?: return false
        if (parcelle.etat != PlotState.EMPTY.name) return false
        val graine = seedParId(seedId) ?: return false
        if (dao.cultureSurParcelle(plotId) != null) return false
        if (!userRepo.spendCoins(graine.prixPieces)) return false

        val maintenant = clock.now().toEpochMilli()
        dao.insererCulture(
            GardenCropEntity(
                plotId = plotId,
                seedId = seedId,
                plantedAtMillis = maintenant,
                dernierArrosageMillis = maintenant,
                arrosages = 1
            )
        )
        dao.majEtatParcelle(plotId, PlotState.GROWING.name)
        return true
    }

    /** Arrose une culture. Consomme une unité d'eau. */
    suspend fun arroser(plotId: Int): Boolean {
        val etat = dao.etat() ?: return false
        if (etat.eau < 1) return false
        val culture = dao.cultureSurParcelle(plotId) ?: return false

        dao.majCulture(
            culture.copy(
                dernierArrosageMillis = clock.now().toEpochMilli(),
                arrosages = culture.arrosages + 1
            )
        )
        dao.sauverEtat(etat.copy(eau = etat.eau - 1))
        return true
    }

    /**
     * Récolte une culture arrivée à maturité.
     * @return les pièces gagnées, ou null si la culture n'est pas prête.
     */
    suspend fun recolter(plotId: Int): Int? {
        val culture = dao.cultureSurParcelle(plotId) ?: return null
        val graine = seedParId(culture.seedId) ?: return null
        val parcelle = dao.parcelle(plotId) ?: return null
        val sol = SoilType.parId(parcelle.solId)

        val maintenant = clock.now().toEpochMilli()
        val minutesDepuisArrosage = (maintenant - culture.dernierArrosageMillis) / 60_000
        val etatCulture = CropGrowthEngine.etat(
            graine, sol, culture.minutesCumulees, minutesDepuisArrosage
        )
        if (!etatCulture.prete) return null

        val duree = CropGrowthEngine.dureeTotaleMinutes(graine, sol)
        val qualite = CropGrowthEngine.qualite(
            arrosagesEffectues = culture.arrosages,
            arrosagesAttendus = (duree / DUREE_ARROSAGE_MINUTES).toInt().coerceAtLeast(1),
            revisionsPendantCulture = culture.revisionsPendantCulture
        )
        val pieces = CropGrowthEngine.rendement(graine, qualite, etatCulture.enRepos)

        userRepo.addCoins(pieces)
        dao.marquerRecoltee(culture.id)
        dao.majEtatParcelle(plotId, PlotState.EMPTY.name)

        // Un peu de compost à chaque récolte, pour amorcer l'économie.
        dao.etat()?.let { dao.sauverEtat(it.copy(compost = it.compost + 1)) }
        return pieces
    }

    // --- Apprentissage ----------------------------------------------------

    /**
     * Crédite les révisions en eau, plafond compris.
     * @return l'eau réellement gagnée, et si le plafond est atteint.
     */
    suspend fun crediterRevisions(
        bonnesReponses: Int,
        cartesDues: Int
    ): LearningRewardEngine.Gain {
        val etat = dao.etat() ?: return LearningRewardEngine.Gain(0, 0, false)

        // Seules les cartes réellement dues rapportent : réviser en boucle des
        // cartes déjà connues ne doit pas produire de ressources.
        val gouttesGagnees = bonnesReponses.coerceAtMost(cartesDues)
        val gain = LearningRewardEngine.convertir(
            gouttesAccumulees = etat.gouttes + gouttesGagnees,
            eauDejaGagneeAujourdhui = etat.eauGagneeAujourdhui
        )

        dao.sauverEtat(
            etat.copy(
                gouttes = gain.gouttes,
                eau = LearningRewardEngine.ajouterEau(etat.eau, gain.eauCreditee),
                eauGagneeAujourdhui = etat.eauGagneeAujourdhui + gain.eauCreditee
            )
        )

        // Les révisions comptent aussi pour la qualité des cultures en cours.
        for (culture in dao.culturesEnCours()) {
            dao.majCulture(
                culture.copy(
                    revisionsPendantCulture = culture.revisionsPendantCulture + bonnesReponses
                )
            )
        }
        return gain
    }

    companion object {
        /** Durée pendant laquelle un arrosage garde la terre humide. */
        const val DUREE_ARROSAGE_MINUTES = 240L
        const val COUT_NETTOYAGE = 120
    }
}

/** Horloge réelle. Isolée pour que les tests puissent la remplacer. */
class SystemGardenClock : GardenClock {
    override fun now(): Instant = Instant.now()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    override fun currentDayId(): String = LocalDate.now().toString()
}
