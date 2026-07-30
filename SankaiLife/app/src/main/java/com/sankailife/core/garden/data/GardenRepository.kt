package com.sankailife.core.garden.data

import android.os.SystemClock
import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.domain.*
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    /**
     * Taille du terrain. 4 × 4 : assez grand pour dépasser l'écran et donner
     * un sens au déplacement de caméra, assez petit pour rester lisible.
     */
    private val colonnes = 4
    private val nombreParcelles = colonnes * 4

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
        // Ajoute les parcelles manquantes sans toucher aux existantes : un
        // agrandissement du terrain ne doit jamais réinitialiser les cultures
        // déjà en place.
        val existantes = dao.parcelles()
        val manquantes = (0 until nombreParcelles)
            .filter { index -> existantes.none { it.id == index } }

        if (manquantes.isNotEmpty()) {
            dao.sauverParcelles(
                manquantes.map { index ->
                    when {
                        index < 4 -> GardenPlotEntity(index, PlotState.EMPTY.name, "terre", 1)
                        index < 8 -> GardenPlotEntity(index, PlotState.UNCLEARED.name, "terre", 1)
                        // Les rangées suivantes s'ouvrent au fil des arènes.
                        else -> GardenPlotEntity(
                            index, PlotState.LOCKED.name,
                            if (index >= 12) "sable" else "terre",
                            areneRequise = 2 + (index - 8) / 4
                        )
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

    /** Ce qu'une récolte a produit, pour que l'écran puisse la nommer. */
    data class Recolte(val graine: Seed, val qualite: HarvestQuality)

    /**
     * Récolte une culture arrivée à maturité.
     *
     * Ne rapporte aucune pièce : la récolte devient une caisse posée sur le
     * terrain, à ranger au dépôt puis à vendre au marchand. Le gain immédiat
     * a été retiré volontairement — c'est ce qui donne un rôle au dépôt.
     *
     * @return la récolte produite, ou null si la plante n'est pas prête ou si
     *         le terrain est saturé de caisses.
     */
    suspend fun recolter(plotId: Int): Recolte? {
        if (DepotEngine.terrainSature(dao.nombreCaisses())) return null

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

        dao.marquerRecoltee(culture.id)
        dao.majEtatParcelle(plotId, PlotState.EMPTY.name)
        dao.poserCaisse(
            GardenCrateEntity(
                seedId = graine.id,
                qualite = qualite.name,
                creeALeMillis = maintenant
            )
        )

        // Un peu de compost à chaque récolte, pour amorcer l'économie.
        dao.etat()?.let { dao.sauverEtat(it.copy(compost = it.compost + 1)) }
        return Recolte(graine, qualite)
    }

    // --- Dépôt central ----------------------------------------------------

    val caissesFlow = dao.observerCaisses()
    val inventaireFlow = dao.observerInventaire()

    /**
     * Transporte toutes les caisses posées jusqu'au dépôt.
     *
     * L'ensemble tient dans une transaction : sans elle, une interruption
     * entre l'écriture du stock et le retrait des caisses ferait compter la
     * même récolte deux fois. C'est aussi ce qui rend l'appel réentrant — un
     * double appui sur « Ranger » ne peut pas créditer deux fois, la seconde
     * transaction ne trouvant plus de caisse.
     *
     * @return le nombre de caisses rangées.
     */
    suspend fun rangerCaisses(): Int = db.withTransaction {
        val caisses = dao.caisses()
        if (caisses.isEmpty()) return@withTransaction 0

        caisses.groupBy { it.seedId to it.qualite }.forEach { (couple, lot) ->
            val (seedId, qualite) = couple
            val cle = "${seedId}_$qualite"
            val existant = dao.ligneInventaire(cle)
            dao.sauverInventaire(
                GardenInventoryEntity(
                    cle = cle,
                    seedId = seedId,
                    qualite = qualite,
                    quantite = (existant?.quantite ?: 0) + lot.size
                )
            )
        }
        dao.retirerCaisses(caisses.map { it.id })
        caisses.size
    }

    /**
     * Vend une quantité de stock au marchand.
     *
     * Refusée hors des heures d'ouverture : c'est la seule contrainte que le
     * cycle jour / nuit impose réellement au joueur.
     *
     * @return les pièces gagnées, ou null si la vente est impossible.
     */
    suspend fun vendre(seedId: String, qualite: HarvestQuality, quantite: Int): Int? {
        if (quantite <= 0) return null
        if (!DayNightEngine.magasinOuvert(clock.now().atZone(ZoneId.systemDefault()).toLocalTime())) {
            return null
        }
        val graine = seedParId(seedId) ?: return null

        // Le retrait porte sa propre condition : si le stock est insuffisant,
        // aucune ligne n'est touchée et rien n'est crédité.
        val cle = DepotEngine.cle(seedId, qualite)
        if (dao.retirerDuStock(cle, quantite) == 0) return null

        val pieces = DepotEngine.prixUnitaire(graine, qualite, clock.currentDayId()) * quantite
        userRepo.addCoins(pieces)
        return pieces
    }

    /** Vend tout le stock d'un coup. @return les pièces totales. */
    suspend fun vendreTout(): Int {
        var total = 0
        for (ligne in dao.observerInventaire().first()) {
            val qualite = runCatching { HarvestQuality.valueOf(ligne.qualite) }
                .getOrDefault(HarvestQuality.NORMALE)
            total += vendre(ligne.seedId, qualite, ligne.quantite) ?: 0
        }
        return total
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

    // --- Défi souvenir ----------------------------------------------------

    /**
     * Prépare le défi lié à la dernière notification, s'il y en a un.
     * @return null si aucune notification récente n'est en attente.
     */
    suspend fun defiSouvenir(): MemoChallengeEngine.Defi? {
        val maintenant = clock.now().toEpochMilli()
        val limite = maintenant - MemoChallengeEngine.VALIDITE_HEURES * 3_600_000
        val trace = dao.dernierDefiDisponible(limite) ?: return null

        if (!MemoChallengeEngine.estProposable(trace.reclame, trace.envoyeALeMillis, maintenant)) {
            return null
        }

        // Les leurres viennent du même module : piocher ailleurs rendrait la
        // bonne réponse reconnaissable au simple ton de la phrase.
        val autres = db.memoDao().getLinesOnce(trace.profileId).map { it.text }
        if (autres.size < 2) return null

        return MemoChallengeEngine.Defi(
            challengeId = trace.id,
            nomModule = trace.nomModule,
            bonneReponse = trace.texte,
            options = MemoChallengeEngine.construireOptions(trace.texte, autres)
        )
    }

    /**
     * Enregistre la réponse au défi.
     *
     * Le marquage sert de verrou : la mise à jour ne touche une ligne que si
     * elle n'était pas déjà réclamée. Un double appui ne crédite donc rien
     * une seconde fois.
     *
     * @return la récompense réellement accordée.
     */
    suspend fun repondreDefiSouvenir(
        challengeId: Long,
        reussi: Boolean
    ): MemoChallengeEngine.Recompense {
        val aucune = MemoChallengeEngine.Recompense(0, 0)
        if (dao.marquerDefiReclame(challengeId) == 0) return aucune

        val recompense = MemoChallengeEngine.recompense(reussi)
        if (recompense.eau > 0) {
            dao.etat()?.let {
                dao.sauverEtat(it.copy(eau = LearningRewardEngine.ajouterEau(it.eau, recompense.eau)))
            }
        }
        if (recompense.pieces > 0) userRepo.addCoins(recompense.pieces)
        return recompense
    }

    /**
     * Bonus de croissance offert par une session Focus terminée.
     * Appliqué à toutes les cultures en cours : la concentration profite au
     * jardin entier, pas à une parcelle choisie.
     */
    suspend fun appliquerBonusFocus() {
        for (culture in dao.culturesEnCours()) {
            dao.majCulture(
                culture.copy(
                    minutesCumulees = culture.minutesCumulees + LearningRewardEngine.BONUS_FOCUS_MINUTES
                )
            )
        }
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
