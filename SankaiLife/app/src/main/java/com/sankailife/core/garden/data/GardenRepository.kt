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
    /** Ce qu'il s'est passé pendant l'absence, à raconter au retour. */
    data class Ouverture(
        val verdict: TrustedTimeEngine.Verdict,
        val rapportMimos: MimoEngine.Rapport = MimoEngine.Rapport()
    )

    suspend fun ouvrirJardin(): Ouverture {
        initialiser()
        val etat = dao.etat() ?: return Ouverture(TrustedTimeEngine.Verdict.COHERENT)

        val heureMurale = clock.now().toEpochMilli()
        val elapsed = clock.elapsedRealtimeMillis()

        val resultat = TrustedTimeEngine.evaluer(
            TrustedTimeState(etat.derniereHeureMurale, etat.dernierElapsedRealtime),
            heureMurale, elapsed
        )

        if (resultat.minutesRetenues > 0) {
            appliquerCroissance(resultat.minutesRetenues, heureMurale)
        }

        // Les Mimos travaillent après la croissance : ils doivent voir le
        // jardin tel qu'il est au retour, pas tel qu'il était au départ.
        // Sinon ils récolteraient des plantes qui n'étaient pas encore mûres.
        val rapport = if (resultat.minutesRetenues > 0) {
            appliquerTravailMimos(
                debutMillis = heureMurale - resultat.minutesRetenues * 60_000,
                finMillis = heureMurale
            )
        } else MimoEngine.Rapport()

        // Le plafond d'eau se remet à zéro au changement de jour.
        val jour = clock.currentDayId()
        val remise = jour != etat.jourPlafond

        // L'état est relu : les Mimos viennent de dépenser eau et compost,
        // écrire la copie d'origine annulerait leur travail.
        val etatCourant = dao.etat() ?: etat
        dao.sauverEtat(
            etatCourant.copy(
                derniereHeureMurale = heureMurale,
                dernierElapsedRealtime = elapsed,
                jourPlafond = jour,
                eauGagneeAujourdhui = if (remise) 0 else etatCourant.eauGagneeAujourdhui
            )
        )
        return Ouverture(resultat.verdict, rapport)
    }

    // --- Mimos -------------------------------------------------------------

    val mimosFlow = dao.observerMimos()

    /**
     * Embauche un Mimo. Le prix monte avec le nombre déjà employés du même
     * type : sans cela, la stratégie optimale serait d'acheter dix arroseurs
     * et de ne plus jamais toucher au jardin.
     */
    suspend fun embaucherMimo(type: MimoEngine.Type): Boolean {
        val deja = dao.compterMimosDeType(type.name)
        val prix = coutEmbauche(type, deja)
        if (!userRepo.spendCoins(prix)) return false

        dao.embaucher(
            GardenMimoEntity(
                type = type.name,
                nom = MimoEngine.NOMS.random(),
                embaucheMillis = clock.now().toEpochMilli()
            )
        )
        return true
    }

    /**
     * Rejoue le travail des Mimos sur la période d'absence.
     *
     * Chaque étape est plafonnée par ce qui existe réellement dans le jardin :
     * un récolteur ne récolte que des plantes mûres, un transporteur ne porte
     * que des caisses posées. Le temps donne un maximum, jamais un minimum.
     */
    private suspend fun appliquerTravailMimos(
        debutMillis: Long,
        finMillis: Long
    ): MimoEngine.Rapport {
        val mimos = dao.mimos()
        if (mimos.isEmpty()) return MimoEngine.Rapport()

        val minutes = MimoEngine.minutesOuvrees(debutMillis, finMillis)
        if (minutes <= 0) return MimoEngine.Rapport()

        var rapport = MimoEngine.Rapport()
        var manqueCompost = false

        // L'ordre suit le circuit : arroser, récolter, transporter, vendre,
        // replanter. Le récolteur profite ainsi de l'arrosage du même retour.
        for (type in MimoEngine.Type.entries) {
            val effectif = mimos.count { it.type == type.name }
            if (effectif == 0) continue

            val compost = dao.etat()?.compost ?: 0
            val budget = MimoEngine.actions(type, minutes, compost) * effectif
            if (budget == 0) {
                if (compost <= 0) manqueCompost = true
                continue
            }

            val bilan = when (type) {
                MimoEngine.Type.ARROSEUR -> Bilan(travailArroseur(budget))
                MimoEngine.Type.RECOLTEUR -> Bilan(travailRecolteur(budget))
                MimoEngine.Type.TRANSPORTEUR -> travailTransporteur()
                MimoEngine.Type.VENDEUR -> travailVendeur(budget)
                MimoEngine.Type.PLANTEUR -> Bilan(travailPlanteur(budget))
            }
            if (bilan.actions == 0) continue

            depenserCompost(bilan.actions * MimoEngine.COMPOST_PAR_ACTION)
            rapport = when (type) {
                MimoEngine.Type.ARROSEUR -> rapport.copy(arrosages = bilan.actions)
                MimoEngine.Type.RECOLTEUR -> rapport.copy(recoltes = bilan.actions)
                MimoEngine.Type.TRANSPORTEUR -> rapport.copy(transports = bilan.effet)
                MimoEngine.Type.VENDEUR ->
                    rapport.copy(ventes = bilan.actions, piecesGagnees = bilan.effet)
                MimoEngine.Type.PLANTEUR -> rapport.copy(plantations = bilan.actions)
            }
        }
        return rapport.copy(compostManquant = manqueCompost)
    }

    /**
     * Résultat d'une passe de travail.
     *
     * [actions] est ce qui est facturé en compost, [effet] ce qui est raconté
     * au joueur. Les deux diffèrent pour le transporteur — un seul trajet
     * ramène plusieurs caisses — et pour le vendeur, dont l'effet est en
     * pièces. Les distinguer évite un champ mutable partagé sur le dépôt.
     */
    private data class Bilan(val actions: Int, val effet: Int = actions)

    /** Un trajet ramène toutes les caisses posées : c'est une seule action. */
    private suspend fun travailTransporteur(): Bilan {
        val rangees = rangerCaisses()
        return if (rangees == 0) Bilan(0) else Bilan(actions = 1, effet = rangees)
    }

    private suspend fun depenserCompost(quantite: Int) {
        dao.etat()?.let {
            dao.sauverEtat(it.copy(compost = (it.compost - quantite).coerceAtLeast(0)))
        }
    }

    /** Arrose en priorité ce qui a soif, sinon rien. */
    private suspend fun travailArroseur(budget: Int): Int {
        var faites = 0
        val maintenant = clock.now().toEpochMilli()
        for (culture in dao.culturesEnCours()) {
            if (faites >= budget) break
            val etat = dao.etat() ?: break
            if (etat.eau < 1) break

            val minutesDepuis = (maintenant - culture.dernierArrosageMillis) / 60_000
            if (minutesDepuis < DUREE_ARROSAGE_MINUTES) continue

            dao.majCulture(
                culture.copy(dernierArrosageMillis = maintenant, arrosages = culture.arrosages + 1)
            )
            dao.sauverEtat(etat.copy(eau = etat.eau - 1))
            faites++
        }
        return faites
    }

    private suspend fun travailRecolteur(budget: Int): Int {
        var faites = 0
        for (parcelle in dao.parcelles()) {
            if (faites >= budget) break
            if (recolter(parcelle.id) != null) faites++
        }
        return faites
    }

    /**
     * Le vendeur n'est pas soumis à l'horaire du marchand.
     *
     * Son travail est reconstitué : [MimoEngine.minutesOuvrees] garantit déjà
     * qu'il n'a compté que des minutes de jour. Lui réappliquer le contrôle
     * d'heure au moment du retour empêcherait toute vente automatique chez
     * quelqu'un qui ouvre l'application le soir — c'est-à-dire la plupart.
     */
    private suspend fun travailVendeur(budget: Int): Bilan {
        var faites = 0
        var pieces = 0
        for (ligne in dao.observerInventaire().first()) {
            if (faites >= budget) break
            val qualite = runCatching { HarvestQuality.valueOf(ligne.qualite) }
                .getOrDefault(HarvestQuality.NORMALE)
            pieces += vendreSansHoraire(ligne.seedId, qualite, ligne.quantite) ?: continue
            faites++
        }
        return Bilan(actions = faites, effet = pieces)
    }

    /**
     * Replante ce qui vient d'être récolté, si les pièces suivent.
     * Choisit la graine la moins chère compatible : un planteur automatique ne
     * doit jamais vider la bourse sans qu'on l'ait décidé.
     */
    private suspend fun travailPlanteur(budget: Int): Int {
        var faites = 0
        for (parcelle in dao.parcelles()) {
            if (faites >= budget) break
            if (parcelle.etat != PlotState.EMPTY.name) continue

            val sol = SoilType.parId(parcelle.solId)
            val graine = ALL_SEEDS
                .filter { it.solRequis == sol }
                .minByOrNull { it.prixPieces } ?: continue

            if (planter(parcelle.id, graine.id)) faites++ else break
        }
        return faites
    }

    /** Prix d'embauche, croissant avec l'effectif déjà en place. */
    fun coutEmbauche(type: MimoEngine.Type, dejaEmployes: Int): Int =
        type.coutEmbauche * (dejaEmployes + 1)

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
        val heure = clock.now().atZone(ZoneId.systemDefault()).toLocalTime()
        if (!DayNightEngine.magasinOuvert(heure)) return null
        return vendreSansHoraire(seedId, qualite, quantite)
    }

    /**
     * La transaction elle-même, sans contrôle d'heure.
     *
     * Séparée de [vendre] pour que le vendeur automatique puisse l'appeler :
     * son travail est reconstitué après coup, l'heure du retour n'a rien à
     * voir avec l'heure où il a vendu.
     */
    private suspend fun vendreSansHoraire(
        seedId: String,
        qualite: HarvestQuality,
        quantite: Int
    ): Int? {
        if (quantite <= 0) return null
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
