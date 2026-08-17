package com.sankailife.core.garden.data

import android.os.SystemClock
import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val synchronisationMutex = Mutex()

    val etatFlow = dao.observerEtat()
    val parcellesFlow = dao.observerParcelles()
    val culturesFlow = dao.observerCultures()

    /**
     * Prépare le jardin au premier lancement.
     *
     * Quatre cases au centre du plan, et rien d'autre. Le reste n'existe pas
     * en base tant qu'il n'est pas découvert : créer les 1 600 cases de la
     * grille logique d'avance remplirait la base pour des positions que le
     * joueur n'atteindra jamais.
     */
    suspend fun initialiser() {
        if (dao.etat() == null) {
            dao.sauverEtat(GardenStateEntity(jourPlafond = clock.currentDayId()))
        }

        val existantes = dao.parcelles()
        if (existantes.isEmpty()) {
            val maintenant = clock.now().toEpochMilli()
            dao.sauverParcelles(
                ExpansionEngine.casesInitiales().map { cle ->
                    GardenPlotEntity(
                        id = cle,
                        etat = PlotState.EMPTY.name,
                        solId = SoilType.TERRE.id,
                        deblocage = ExpansionEngine.Deblocage.DEBLOQUEE.name,
                        terrain = ExpansionEngine.Terrain.ORDINAIRE.name,
                        humidite = 0.55f,
                        dernierCalculHumidite = maintenant
                    )
                }
            )
        }
        revelerFrontiere()
    }

    /**
     * Fait reculer le brouillard d'un cran.
     *
     * Seules les cases adjacentes à une case possédée sont matérialisées en
     * base. Le reste du plan reste une abstraction — ce qui garde la table
     * proportionnelle à ce que le joueur a réellement exploré.
     */
    private suspend fun revelerFrontiere() {
        val toutes = dao.parcelles()
        val possedees = toutes
            .filter { it.deblocage == ExpansionEngine.Deblocage.DEBLOQUEE.name }
            .map { it.id }.toSet()
        if (possedees.isEmpty()) return

        val connues = toutes.map { it.id }.toSet()
        val nouvelles = ExpansionEngine.frontiere(possedees) - connues
        if (nouvelles.isEmpty()) return

        dao.sauverParcelles(
            nouvelles.map { cle ->
                val terrain = ExpansionEngine.terrainDe(cle)
                GardenPlotEntity(
                    id = cle,
                    etat = if (terrain.aNettoyer) PlotState.UNCLEARED.name else PlotState.EMPTY.name,
                    solId = terrain.sol.id,
                    deblocage = ExpansionEngine.Deblocage.DECOUVERTE.name,
                    terrain = terrain.name,
                    humidite = 0.4f,
                    dernierCalculHumidite = clock.now().toEpochMilli()
                )
            }
        )
    }

    /**
     * Lance le chantier d'une case découverte.
     *
     * L'adjacence est revérifiée ici et pas seulement à l'affichage : c'est la
     * règle du jeu, elle doit tenir même si un écran se trompe.
     */
    suspend fun lancerChantier(cle: Int): Boolean = db.withTransaction {
        val parcelle = dao.parcelle(cle) ?: return@withTransaction false
        if (parcelle.deblocage != ExpansionEngine.Deblocage.DECOUVERTE.name) {
            return@withTransaction false
        }

        val possedees = dao.parcelles()
            .filter { it.deblocage == ExpansionEngine.Deblocage.DEBLOQUEE.name }
            .map { it.id }.toSet()
        if (!ExpansionEngine.estAchetable(cle, possedees)) return@withTransaction false

        val terrain = ExpansionEngine.Terrain.parNom(parcelle.terrain)
        val cout = ExpansionEngine.cout(cle, terrain)
        val fin = clock.now().toEpochMilli() +
            ExpansionEngine.dureeChantierMinutes(cle, terrain) * 60_000

        // La reservation conditionnelle vient avant le debit. Dans cette
        // transaction, un second appui ne peut donc ni relancer ni repayer le
        // meme chantier.
        if (dao.lancerChantierSiDecouverte(cle, fin) == 0) return@withTransaction false
        if (!userRepo.spendCoins(cout)) {
            dao.annulerChantierSiIdentique(cle, fin)
            return@withTransaction false
        }
        true
    }

    /** Termine les chantiers arrivés à échéance. @return le nombre livré. */
    private suspend fun acheverChantiers(): Int {
        val maintenant = clock.now().toEpochMilli()
        val finis = dao.parcelles().filter {
            it.deblocage == ExpansionEngine.Deblocage.EN_CHANTIER.name &&
                it.chantierFinMillis in 1..maintenant
        }
        if (finis.isEmpty()) return 0

        val acheves = finis.count { dao.acheverChantierSiArrive(it.id, maintenant) > 0 }
        if (acheves == 0) return 0
        revelerFrontiere()
        return acheves
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

    /** Résultat d'une mise à l'heure, réutilisable sans déclencher de récit UI. */
    data class Synchronisation(
        val resultat: TrustedTimeEngine.Resultat,
        val maintenantMillis: Long
    )

    /**
     * Met à jour croissance, humidité et chantiers depuis le dernier repère.
     *
     * Cette opération est idempotente : le repère temporel est écrit dans la
     * même transaction que les effets du temps. Elle ne fait volontairement
     * travailler aucun Mimo et ne crée ni rapport ni défi, afin de pouvoir être
     * appelée chaque minute tant que l'écran est visible.
    */
    suspend fun synchroniserTemps(): Synchronisation {
        return synchronisationMutex.withLock {
            // L'initialisation partage le même verrou : l'ouverture du VM et
            // la première synchronisation de l'écran peuvent arriver ensemble.
            initialiser()
            db.withTransaction {
                val etat = dao.etat()
                val heureMurale = clock.now().toEpochMilli()
                val elapsed = clock.elapsedRealtimeMillis()

                if (etat == null) {
                    return@withTransaction Synchronisation(
                        TrustedTimeEngine.Resultat(TrustedTimeEngine.Verdict.COHERENT, 0L),
                        heureMurale
                    )
                }

                val resultat = TrustedTimeEngine.evaluer(
                    TrustedTimeState(etat.derniereHeureMurale, etat.dernierElapsedRealtime),
                    heureMurale,
                    elapsed
                )
                if (resultat.minutesRetenues > 0) {
                    appliquerCroissance(resultat.minutesRetenues, heureMurale)
                }
                acheverChantiers()

                val jour = clock.currentDayId()
                val remise = jour != etat.jourPlafond
                val courant = dao.etat() ?: etat
                dao.sauverEtat(
                    courant.copy(
                        derniereHeureMurale = heureMurale,
                        dernierElapsedRealtime = elapsed,
                        jourPlafond = jour,
                        eauGagneeAujourdhui = if (remise) 0 else courant.eauGagneeAujourdhui
                    )
                )
                Synchronisation(resultat, heureMurale)
            }
        }
    }

    suspend fun ouvrirJardin(): Ouverture {
        val synchronisation = synchroniserTemps()
        val resultat = synchronisation.resultat

        // Les Mimos travaillent après la croissance : ils doivent voir le
        // jardin tel qu'il est au retour, pas tel qu'il était au départ.
        // Sinon ils récolteraient des plantes qui n'étaient pas encore mûres.
        val rapport = if (resultat.minutesRetenues > 0) {
            appliquerTravailMimos(
                debutMillis = synchronisation.maintenantMillis - resultat.minutesRetenues * 60_000,
                finMillis = synchronisation.maintenantMillis
            )
        } else MimoEngine.Rapport()
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
    ): MimoEngine.Rapport = db.withTransaction {
        val mimos = dao.mimos()
        if (mimos.isEmpty()) return@withTransaction MimoEngine.Rapport()

        val minutes = MimoEngine.minutesOuvrees(debutMillis, finMillis)
        if (minutes <= 0) return@withTransaction MimoEngine.Rapport()

        var rapport = MimoEngine.Rapport()
        var manqueCompost = false

        // L'ordre suit le circuit : arroser, récolter, transporter, vendre,
        // replanter. Le récolteur profite ainsi de l'arrosage du même retour.
        for (type in MimoEngine.Type.entries) {
            val effectif = mimos.count { it.type == type.name }
            if (effectif == 0) continue

            val compost = dao.etat()?.compost ?: 0
            val budget = MimoEngine.actionsEquipe(type, minutes, effectif, compost)
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

            check(depenserCompost(bilan.actions * MimoEngine.COMPOST_PAR_ACTION)) {
                "Le budget compost des Mimos a change pendant la transaction."
            }
            rapport = when (type) {
                MimoEngine.Type.ARROSEUR -> rapport.copy(arrosages = bilan.actions)
                MimoEngine.Type.RECOLTEUR -> rapport.copy(recoltes = bilan.actions)
                MimoEngine.Type.TRANSPORTEUR -> rapport.copy(transports = bilan.effet)
                MimoEngine.Type.VENDEUR ->
                    rapport.copy(ventes = bilan.actions, piecesGagnees = bilan.effet)
                MimoEngine.Type.PLANTEUR -> rapport.copy(plantations = bilan.actions)
            }
        }
        rapport.copy(compostManquant = manqueCompost)
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

    private suspend fun depenserCompost(quantite: Int): Boolean =
        quantite > 0 && dao.depenserCompostSiAssez(quantite) > 0

    /**
     * Arrose ce qui a soif, et seulement ça.
     *
     * Passe par le même chemin assisté que le joueur : un Mimo qui noierait
     * un cactus serait un Mimo qu'on préfère ne pas embaucher.
     */
    private suspend fun travailArroseur(budget: Int): Int {
        var faites = 0
        for (culture in dao.culturesEnCours()) {
            if (faites >= budget) break
            if ((dao.etat()?.eau ?: 0) < 1) break
            if (arroser(culture.plotId, assiste = true)) faites++
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

    /**
     * Fait sécher les sols, puis pousser les plantes.
     *
     * L'ordre compte : l'évaporation d'abord, la croissance ensuite, avec
     * l'humidité **moyenne** de la période. Utiliser l'humidité finale
     * pénaliserait deux fois un joueur absent — le sol a séché *pendant* son
     * absence, il n'était pas sec depuis le début.
     */
    private suspend fun appliquerCroissance(minutes: Long, maintenant: Long) {
        val debut = maintenant - minutes * 60_000
        val minutesDeJour = MimoEngine.minutesOuvrees(debut, maintenant)
        val partNocturne = if (minutes > 0) {
            1f - (minutesDeJour.toFloat() / minutes)
        } else 0f

        val cultures = dao.culturesEnCours().associateBy { it.plotId }

        // Une absence longue traverse plusieurs journées, donc plusieurs
        // météos. Appliquer celle du retour à toute la période ferait pleuvoir
        // rétroactivement sur des journées qui avaient été sèches.
        val segments = WeatherEngine.segments(debut, maintenant)

        for (parcelle in dao.parcelles()) {
            if (parcelle.deblocage != ExpansionEngine.Deblocage.DEBLOQUEE.name) continue

            val sol = SoilType.parId(parcelle.solId)
            val avant = parcelle.humidite
            var courante = avant
            for (segment in segments) {
                courante = MoistureEngine.apresEcoulement(
                    courante, segment.minutes, sol, partNocturne, segment.meteo
                )
            }
            val apres = courante
            val moyenne = (avant + apres) / 2f

            dao.majHumidite(parcelle.id, apres, maintenant)

            val culture = cultures[parcelle.id] ?: continue
            val graine = seedParId(culture.seedId) ?: continue
            val acquises = (minutes * MoistureEngine.facteurCroissance(moyenne, graine)).toLong()
            dao.majCulture(culture.copy(minutesCumulees = culture.minutesCumulees + acquises))
        }
    }

    // --- Actions du joueur ------------------------------------------------

    /**
     * Prépare une parcelle : retire les pierres, ou laboure après une récolte.
     *
     * Le labour est gratuit, le défrichage se paie. C'est la même action côté
     * joueur mais pas le même effort : dégager des rochers d'un terrain vierge
     * n'a rien à voir avec retourner une terre qu'on vient de récolter, et
     * faire payer chaque cycle de culture transformerait l'agriculture en
     * impôt.
     */
    suspend fun nettoyer(plotId: Int): Boolean {
        val parcelle = dao.parcelle(plotId) ?: return false
        if (parcelle.etat != PlotState.UNCLEARED.name) return false

        // Une parcelle déjà cultivée une fois n'a plus de rochers : ce qu'on
        // laboure, c'est sa propre récolte précédente.
        val dejaCultivee = dao.aDejaEteCultivee(plotId) > 0
        if (!dejaCultivee && !userRepo.spendCoins(COUT_NETTOYAGE)) return false

        dao.majEtatParcelle(plotId, PlotState.EMPTY.name)
        return true
    }

    /**
     * Plante une graine. Le prix est débité au moment de la plantation :
     * pas d'inventaire de graines dans le prototype, une transaction de moins
     * à réconcilier.
     */
    suspend fun planter(plotId: Int, seedId: String): Boolean = db.withTransaction {
        val graine = seedParId(seedId) ?: return@withTransaction false
        if (dao.commencerPlantationSiVide(plotId) == 0) return@withTransaction false

        // La parcelle est reservee dans la meme transaction. Si le solde ne
        // suffit pas, on rend uniquement cette reservation encore vide.
        if (!userRepo.spendCoins(graine.prixPieces)) {
            dao.annulerPlantationSansCulture(plotId)
            return@withTransaction false
        }

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
        true
    }

    /**
     * Arrose une parcelle. Consomme une unité d'eau.
     *
     * Verse une quantité dans le sol au lieu de remettre un minuteur à zéro.
     * Conséquence voulue : arroser un sol déjà détrempé gaspille l'eau, et
     * l'arrosage assisté peut donc refuser d'agir.
     *
     * Une parcelle vide peut être arrosée — préparer la terre avant de semer
     * est un geste légitime, l'interdire n'apprendrait rien à personne.
     */
    suspend fun arroser(plotId: Int, assiste: Boolean = false): Boolean = db.withTransaction {
        val parcelle = dao.parcelle(plotId) ?: return@withTransaction false
        if (parcelle.deblocage != ExpansionEngine.Deblocage.DEBLOQUEE.name) {
            return@withTransaction false
        }

        val culture = dao.cultureSurParcelle(plotId)
        val graine = culture?.let { seedParId(it.seedId) }

        // L'assistance évite le gaspillage et, pour un cactus, le dégât.
        if (assiste && !MoistureEngine.aBesoinDEau(parcelle.humidite, graine)) {
            return@withTransaction false
        }
        if (parcelle.humidite >= 0.99f) return@withTransaction false

        // Le debit conditionnel et l'effet vivent dans la meme transaction :
        // deux appels ne peuvent partager la meme unite d'eau ni ecraser le
        // compteur d'arrosages de l'autre.
        if (dao.depenserEauSiAssez(1) == 0) return@withTransaction false
        val maintenant = clock.now().toEpochMilli()
        dao.majHumidite(plotId, MoistureEngine.apresArrosage(parcelle.humidite), maintenant)
        if (culture != null) dao.enregistrerArrosage(plotId, maintenant)
        true
    }

    /** Ce qu'une récolte a produit, pour que l'écran puisse la nommer. */
    data class Recolte(val graine: Seed, val qualite: HarvestQuality)

    /**
     * Arrose la zone couverte par l'arrosoir depuis la case visée.
     *
     * L'arrosoir ne crée pas d'eau : chaque case arrosée coûte son unité. Un
     * meilleur arrosoir fait gagner des gestes, pas des ressources — l'eau
     * reste ce que l'apprentissage a produit, et c'est le seul lien entre le
     * jeu et les révisions.
     *
     * @return le nombre de parcelles réellement arrosées.
     */
    suspend fun arroserZone(cle: Int, assiste: Boolean = true): Int {
        val niveau = dao.etat()?.niveauArrosoir ?: 1
        var faites = 0
        for (cible in ArrosoirEngine.zone(niveau, cle)) {
            if ((dao.etat()?.eau ?: 0) < 1) break
            if (arroser(cible, assiste)) faites++
        }
        return faites
    }

    /**
     * Crédite de l'eau achetée.
     *
     * Refuse si la réserve est déjà pleine, ce qui permet à la boutique de
     * rembourser : encaisser une eau qui déborderait reviendrait à vendre du
     * vide.
     *
     * Cette eau ne compte pas dans le plafond quotidien de l'apprentissage :
     * ce plafond existe pour empêcher de farmer les révisions, pas pour
     * limiter ce qu'on a payé.
     */
    suspend fun ajouterEau(quantite: Int): Boolean {
        val etat = dao.etat() ?: return false
        if (etat.eau >= LearningRewardEngine.CAPACITE_EAU) return false
        dao.sauverEtat(
            etat.copy(eau = LearningRewardEngine.ajouterEau(etat.eau, quantite))
        )
        return true
    }

    /** Crédite du compost acheté. Il n'a pas de plafond. */
    suspend fun ajouterCompost(quantite: Int) {
        if (quantite > 0) dao.crediterCompost(quantite)
    }

    /** Améliore l'arrosoir d'un niveau. */
    suspend fun ameliorerArrosoir(): Boolean {
        val etat = dao.etat() ?: return false
        val cout = ArrosoirEngine.coutAmelioration(etat.niveauArrosoir) ?: return false
        if (!userRepo.spendCoins(cout)) return false
        // L'état est relu : `spendCoins` n'y touche pas, mais rien ne garantit
        // qu'un autre appel n'ait pas modifié l'eau entre-temps.
        val courant = dao.etat() ?: return false
        dao.sauverEtat(courant.copy(niveauArrosoir = courant.niveauArrosoir + 1))
        return true
    }

    /**
     * Combien de parcelles seront sèches dans [heures].
     * Sert à annoncer le besoin au lieu de laisser découvrir un jardin fané.
     */
    suspend fun parcellesASoifAvant(heures: Float): Int {
        val meteo = WeatherEngine.meteoActuelle()
        return dao.parcelles().count { parcelle ->
            if (parcelle.deblocage != ExpansionEngine.Deblocage.DEBLOQUEE.name) return@count false
            val future = MoistureEngine.apresEcoulement(
                parcelle.humidite, (heures * 60).toLong(),
                SoilType.parId(parcelle.solId), meteo = meteo
            )
            val graine = dao.cultureSurParcelle(parcelle.id)?.let { seedParId(it.seedId) }
            MoistureEngine.aBesoinDEau(future, graine)
        }
    }

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
    suspend fun recolter(plotId: Int): Recolte? = db.withTransaction {
        if (DepotEngine.terrainSature(dao.nombreCaisses())) return@withTransaction null

        val culture = dao.cultureSurParcelle(plotId) ?: return@withTransaction null
        val graine = seedParId(culture.seedId) ?: return@withTransaction null
        val parcelle = dao.parcelle(plotId) ?: return@withTransaction null
        val sol = SoilType.parId(parcelle.solId)

        val maintenant = clock.now().toEpochMilli()
        val minutesDepuisArrosage = (maintenant - culture.dernierArrosageMillis) / 60_000
        val etatCulture = CropGrowthEngine.etat(
            graine, sol, culture.minutesCumulees, minutesDepuisArrosage
        )
        if (!etatCulture.prete) return@withTransaction null

        val duree = CropGrowthEngine.dureeTotaleMinutes(graine, sol)
        val qualite = CropGrowthEngine.qualite(
            arrosagesEffectues = culture.arrosages,
            arrosagesAttendus = (duree / DUREE_ARROSAGE_MINUTES).toInt().coerceAtLeast(1),
            revisionsPendantCulture = culture.revisionsPendantCulture
        )

        // Le marqueur conditionnel est le verrou de la recolte. Une seconde
        // transaction ne peut poser ni caisse ni compost pour la meme plante.
        if (dao.marquerRecolteeSiActive(culture.id) == 0) return@withTransaction null
        // La terre sort usée d'une récolte : elle doit être labourée avant
        // d'accueillir une nouvelle graine. Sans cette étape, la houe n'aurait
        // aucun usage et le cycle se réduirait à semer-récolter en boucle.
        check(dao.terminerRecolteSiEnCroissance(plotId) > 0) {
            "La parcelle a change d'etat pendant la recolte."
        }
        dao.poserCaisse(
            GardenCrateEntity(
                seedId = graine.id,
                qualite = qualite.name,
                creeALeMillis = maintenant
            )
        )

        // Un peu de compost à chaque récolte, pour amorcer l'économie.
        dao.crediterCompost(1)
        Recolte(graine, qualite)
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
