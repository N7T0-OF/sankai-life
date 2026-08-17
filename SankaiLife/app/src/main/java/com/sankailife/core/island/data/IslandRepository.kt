package com.sankailife.core.island.data

import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.domain.ALL_SEEDS
import com.sankailife.core.garden.domain.CropGrowthEngine
import com.sankailife.core.garden.domain.MimoEngine
import com.sankailife.core.garden.domain.SoilType
import com.sankailife.core.island.domain.IslandCodec
import com.sankailife.core.island.domain.IslandBuildingEngine
import com.sankailife.core.island.domain.IslandCultureEngine
import com.sankailife.core.island.domain.IslandForetEngine
import com.sankailife.core.island.domain.IslandMimoEngine
import com.sankailife.core.island.domain.IslandStockEngine
import com.sankailife.core.island.domain.IslandTimeEngine
import com.sankailife.core.island.domain.IslandGenerator
import com.sankailife.core.island.domain.IslandSlotEngine
import com.sankailife.core.island.domain.IslandTileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * L'île d'un joueur : sa création, sa relecture et ses achats.
 *
 * Deux exigences commandent tout ce fichier.
 *
 * L'île se génère **une fois**. Une seconde génération écraserait une partie,
 * et il n'existe aucun serveur pour la reconstituer.
 *
 * Un achat ne doit jamais laisser le joueur payé sans parcelle, ni servi sans
 * avoir payé. Les deux écritures — débit et parcelle — vivent dans la même
 * transaction, et le débit vient en premier : il porte déjà sa propre condition
 * de solde.
 */
class IslandRepository(
    private val db: SankaiDatabase,
    private val userRepo: UserRepository
) {
    private val dao = db.islandDao()

    /** L'île relue, ou `null` si le joueur n'en a pas encore. */
    suspend fun ile(): IslandGenerator.Ile? = withContext(Dispatchers.IO) {
        dao.ile()?.let { rehydrater(it) }
    }

    /**
     * Reconstruit une île depuis sa ligne de base.
     *
     * L'empreinte et la taille sont vérifiées : une donnée tronquée se lirait
     * comme une île plus petite et décalerait tout le terrain d'une ligne à
     * l'autre, ce qui donnerait une carte plausible et fausse. Mieux vaut
     * refuser de la lire et laisser l'appelant décider.
     */
    private fun rehydrater(ligne: IslandEntity): IslandGenerator.Ile? {
        if (!IslandCodec.tailleCoherente(ligne.tuiles, ligne.largeur, ligne.hauteur)) return null
        if (IslandCodec.empreinte(ligne.tuiles) != ligne.empreinte) return null

        return IslandGenerator.Ile(
            seed = ligne.seed,
            largeur = ligne.largeur,
            hauteur = ligne.hauteur,
            tuiles = IslandCodec.decoder(ligne.tuiles),
            ponton = ligne.pontonX.takeIf { it >= 0 }
                ?.let { IslandGenerator.Case(it, ligne.pontonY) },
            zoneDepart = ligne.departX.takeIf { it >= 0 }
                ?.let { IslandGenerator.Case(it, ligne.departY) },
            version = ligne.generationVersion
        )
    }

    /**
     * Crée l'île du joueur si elle n'existe pas encore.
     *
     * `creerSiAbsente` s'appuie sur un `INSERT … OR IGNORE` : deux appels
     * concurrents au premier lancement ne peuvent pas produire deux îles, ni
     * écraser celle qui vient d'être écrite. C'est le même verrou que pour la
     * création du profil joueur, et pour la même raison.
     *
     * @return l'île du joueur — celle qui vient d'être créée, ou celle qui
     *   existait déjà.
     */
    suspend fun creerSiAbsente(
        graine: Long = System.currentTimeMillis(),
        nom: String = ""
    ): IslandGenerator.Ile = withContext(Dispatchers.IO) {
        dao.ile()?.let { existante ->
            rehydrater(existante)?.let { return@withContext it }
            // Ligne présente mais illisible. On ne l'écrase pas en silence :
            // l'appelant doit pouvoir proposer une restauration.
            throw IllegalStateException("L'île enregistrée est illisible.")
        }

        val (ile, _) = IslandGenerator.genererJouable(graine)
        val donnees = IslandCodec.encoder(ile.tuiles)

        dao.creerSiAbsente(
            IslandEntity(
                id = 1,
                seed = ile.seed,
                largeur = ile.largeur,
                hauteur = ile.hauteur,
                tuiles = donnees,
                empreinte = IslandCodec.empreinte(donnees),
                generationVersion = ile.version,
                pontonX = ile.ponton?.x ?: -1,
                pontonY = ile.ponton?.y ?: -1,
                departX = ile.zoneDepart?.x ?: -1,
                departY = ile.zoneDepart?.y ?: -1,
                nom = nom,
                creeMillis = System.currentTimeMillis()
            )
        )

        // Relecture plutôt que renvoi direct : si une autre coroutine a gagné
        // la course, c'est son île qui fait foi.
        dao.ile()?.let { rehydrater(it) } ?: ile
    }

    /** Résultat d'un achat, du point de vue de l'appelant. */
    sealed interface Achat {
        data class Reussi(val prixPaye: Int) : Achat
        data class Refuse(val raison: String) : Achat
    }

    /**
     * Achète une parcelle.
     *
     * Le déroulé est volontairement défensif à chaque étape :
     *
     * 1. l'île et le terrain sont relus en base, jamais reçus de l'appelant —
     *    un écran ne doit pas pouvoir demander d'acheter la mer ;
     * 2. le moteur tranche, avec le solde et le niveau réels ;
     * 3. le débit s'exécute sous condition de solde, dans la transaction ;
     * 4. l'insertion de la parcelle est ignorée si la case existe déjà, et un
     *    double appui est alors **remboursé** au lieu d'être encaissé.
     *
     * Sans cette dernière étape, deux appuis rapprochés débitent deux fois pour
     * une seule parcelle.
     */
    suspend fun acheterParcelle(x: Int, y: Int): Achat = withContext(Dispatchers.IO) {
        db.withTransaction {
            val ligne = dao.ile() ?: return@withTransaction Achat.Refuse("Aucune île.")
            val ile = rehydrater(ligne)
                ?: return@withTransaction Achat.Refuse("L'île enregistrée est illisible.")

            if (x !in 0 until ile.largeur || y !in 0 until ile.hauteur) {
                return@withTransaction Achat.Refuse("Cette case n'est pas sur l'île.")
            }

            val cle = y * ile.largeur + x
            val type = ile.type(x, y)
            val possedees = dao.nombreParcelles()
            val deja = dao.parcelles().any { it.cle == cle }
            // Niveau et solde relus en base, pas recus de l'ecran : un
            // appelant ne doit pas pouvoir annoncer son propre niveau.
            val utilisateur = db.userDao().getUserOnce()
                ?: return@withTransaction Achat.Refuse("Profil introuvable.")

            // Une case cachée sous un feuillage ne se vend pas : la vendre
            // reviendrait à encaisser pour un terrain inutilisable.
            if (type != IslandTileType.FOREST && (x to y) in casesSousArbres(ile)) {
                return@withTransaction Achat.Refuse("Un arbre recouvre cette case.")
            }

            val verdict = IslandSlotEngine.peutAcheter(
                type = type,
                dejaAchetee = deja,
                occupee = dao.batiments().any { occupe(it, x, y) },
                niveauJoueur = utilisateur.level,
                parcellesPossedees = possedees,
                pieces = utilisateur.coins
            )
            if (verdict is IslandSlotEngine.Verdict.Non) {
                return@withTransaction Achat.Refuse(verdict.raison)
            }
            val prix = (verdict as IslandSlotEngine.Verdict.Oui).prix

            if (prix > 0 && !userRepo.spendCoins(prix)) {
                return@withTransaction Achat.Refuse("Il te manque des pièces.")
            }

            val insere = dao.acheterParcelle(
                IslandSlotEntity(
                    cle = cle, x = x, y = y,
                    prixPaye = prix,
                    aDegager = type == IslandTileType.FOREST || type == IslandTileType.ROCK,
                    acheteeMillis = System.currentTimeMillis()
                )
            )
            if (insere == -1L) {
                // La case a été achetée entre le contrôle et l'écriture. On rend
                // l'argent : encaisser sans livrer serait le pire des deux maux.
                if (prix > 0) userRepo.refundCoins(prix)
                return@withTransaction Achat.Refuse("Cette parcelle vient d'être achetée.")
            }

            Achat.Reussi(prix)
        }
    }

    // ------------------------------------------------------------------
    // Cultures
    // ------------------------------------------------------------------

    /** Coût d'un arrosage, en unités d'eau. */
    private val EAU_PAR_ARROSAGE = 1

    /** Résultat d'une action agricole. */
    sealed interface Geste {
        data class Fait(val message: String) : Geste
        data class Refuse(val raison: String) : Geste
    }

    /**
     * Rattrape la croissance de toutes les cultures.
     *
     * Rien ne tourne en arrière-plan : la plante n'avance pas, on recalcule où
     * elle en serait. C'est ce qui permet à une culture de progresser
     * application fermée sans consommer la moindre batterie.
     *
     * `dernierCalculMillis` empêche de compter deux fois les mêmes minutes si
     * l'écran est rouvert plusieurs fois d'affilée.
     */
    suspend fun rafraichirCultures(maintenant: Long = System.currentTimeMillis()): Long =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                rafraichirCulturesDansTransaction(maintenant)
            }
        }

    /**
     * Rafraichit les cultures et renvoie l'intervalle qui existait avant
     * l'avancement de leurs curseurs. L'appelant peut ainsi donner exactement
     * le meme temps aux Mimos.
     */
    private suspend fun rafraichirCulturesDansTransaction(maintenant: Long): Long {
        val parcelles = dao.parcelles()
        val intervalleMimos = IslandTimeEngine.minutesDepuisDerniereVisite(
            parcelles.asSequence()
                .filter { it.graineId.isNotBlank() }
                .map { it.dernierCalculMillis.takeIf { v -> v > 0 } ?: it.planteeMillis }
                .asIterable(),
            maintenant
        )

        parcelles.forEach { parcelle ->
            if (parcelle.graineId.isBlank()) return@forEach
            val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
                ?: return@forEach
            val depuis = parcelle.dernierCalculMillis.takeIf { it > 0 }
                ?: parcelle.planteeMillis
            val ecoulees = IslandTimeEngine.minutesRetenues(depuis, maintenant)
            if (ecoulees <= 0L) return@forEach

            // En cas d'avance murale excessive, seule la fenetre bornee est
            // simulee. Le curseur passe tout de meme a maintenant afin que le
            // meme saut d'horloge ne puisse pas etre reclame en boucle.
            val debutRetenu = maintenant - ecoulees * 60_000L
            val arrosees = IslandCultureEngine.minutesArrosees(
                parcelle.dernierArrosageMillis, debutRetenu, maintenant
            )
            val acquises = CropGrowthEngine.minutesAcquises(ecoulees, arrosees)
            val cumulees = parcelle.minutesCumulees + acquises

            val sol = SoilType.parId(parcelle.solId)
            val etat = CropGrowthEngine.etat(
                seed = graine,
                sol = sol,
                minutesCumulees = cumulees,
                minutesDepuisArrosage =
                    ((maintenant - parcelle.dernierArrosageMillis) / 60_000L).coerceAtLeast(0L)
            )

            dao.majParcelle(
                parcelle.copy(
                    minutesCumulees = cumulees,
                    dernierCalculMillis = maintenant,
                    etat = IslandCultureEngine.etatApres(etat.prete, etat.besoinEau).name
                )
            )
        }
        return intervalleMimos
    }

    /** Dégage bois ou rocher. */
    suspend fun degager(x: Int, y: Int): Geste = agir(x, y) { parcelle ->
        if (dao.degagerSiBesoin(parcelle.cle) == 0) {
            Geste.Refuse("Il n'y a rien à dégager ici.")
        } else {
            Geste.Fait("Parcelle dégagée.")
        }
    }

    /** Prépare la terre avant le semis. */
    suspend fun preparer(x: Int, y: Int): Geste = agir(x, y) { parcelle ->
        if (dao.preparerSiVide(parcelle.cle) == 0) {
            Geste.Refuse(
                if (parcelle.aDegager) "Il faut d'abord dégager la parcelle."
                else "Cette parcelle est déjà travaillée."
            )
        } else {
            Geste.Fait("Terre préparée.")
        }
    }

    /**
     * Sème une graine.
     *
     * Le sol est vérifié avant de payer : un cactus veut du sable, et le lui
     * refuser après avoir débité serait pire que de le refuser tout de suite.
     */
    suspend fun semer(x: Int, y: Int, graineId: String): Geste = agir(x, y) { parcelle ->
        val graine = ALL_SEEDS.firstOrNull { it.id == graineId }
            ?: return@agir Geste.Refuse("Graine inconnue.")
        val sol = SoilType.parId(parcelle.solId)
        if (!IslandCultureEngine.grainePlantable(graine, sol)) {
            return@agir Geste.Refuse("${graine.nom} ne pousse pas sur ce sol.")
        }
        if (graine.prixPieces > 0 && !userRepo.spendCoins(graine.prixPieces)) {
            return@agir Geste.Refuse("Il te manque ${graine.prixPieces} pièces.")
        }
        if (dao.semerSiPreparee(parcelle.cle, graineId, System.currentTimeMillis()) == 0) {
            // La parcelle a change d'etat entre le controle et l'ecriture.
            if (graine.prixPieces > 0) userRepo.refundCoins(graine.prixPieces)
            return@agir Geste.Refuse("La terre doit être préparée avant de semer.")
        }
        Geste.Fait("${graine.nom} semée.")
    }

    /**
     * Arrose une culture, aux frais de la réserve d'eau.
     *
     * C'est ce qui relie l'île à l'apprentissage. L'eau ne s'achète pas : elle
     * se gagne en révisant. Sans ce coût, l'île serait un jeu de ferme posé à
     * côté d'une application d'apprentissage, et réviser n'y servirait à rien.
     *
     * La réserve est celle du Jardin, volontairement : c'est l'eau du joueur,
     * pas celle d'un lieu. Deux réserves séparées obligeraient à choisir où
     * réviser « rapporte », ce qui n'a aucun sens.
     *
     * Le débit passe avant l'arrosage et porte sa propre condition ; il est
     * rendu si la parcelle s'avère vide, pour ne jamais facturer un geste qui
     * n'a pas eu lieu.
     */
    suspend fun arroser(x: Int, y: Int): Geste = agir(x, y) { parcelle ->
        if (parcelle.graineId.isBlank()) {
            return@agir Geste.Refuse("Il n'y a rien à arroser.")
        }
        val jardin = db.gardenDao()
        if (jardin.depenserEauSiAssez(EAU_PAR_ARROSAGE) == 0) {
            return@agir Geste.Refuse("Plus d'eau. Révise des cartes pour en regagner.")
        }
        if (dao.arroserSiCulture(parcelle.cle, System.currentTimeMillis()) == 0) {
            // La culture a disparu entre le controle et l'ecriture : on rend
            // l'eau plutot que de facturer un arrosage qui n'a pas eu lieu.
            jardin.crediterEau(EAU_PAR_ARROSAGE)
            return@agir Geste.Refuse("Il n'y a rien à arroser.")
        }
        Geste.Fait("Parcelle arrosée. −$EAU_PAR_ARROSAGE 💧")
    }

    /**
     * Applique le travail des Mimos depuis la dernière visite.
     *
     * Rien n'a tourné en arrière-plan : on calcule ce qu'ils auraient accompli
     * et on l'applique d'un coup. L'écran ne doit donc jamais prétendre montrer
     * un Mimo en train de courir — il montrerait un travail déjà fait.
     *
     * Appelée après [rafraichirCultures], jamais avant : un Mimo ne peut
     * récolter que ce que la croissance a rendu mûr.
     *
     * @return un compte rendu à afficher, ou `null` s'ils n'ont rien fait.
     */
    suspend fun travailDesMimos(
        maintenant: Long = System.currentTimeMillis(),
        minutesEcoulees: Long? = null
    ): String? = withContext(Dispatchers.IO) {
        db.withTransaction {
            travailDesMimosDansTransaction(maintenant, minutesEcoulees)
        }
    }

    private suspend fun travailDesMimosDansTransaction(
        maintenant: Long,
        minutesEcoulees: Long?
    ): String? {
        val types = db.gardenDao().mimos()
            .mapNotNull { MimoEngine.Type.parNom(it.type) }
        if (types.isEmpty()) return null

        val parcelles = dao.parcelles().filter { it.graineId.isNotBlank() }
        if (parcelles.isEmpty()) return null

        // Le chemin combine fournit l'intervalle capture avant que la
        // croissance n'avance les curseurs. Le calcul de repli conserve
        // un appel direct sur cette methode sans faire confiance a une
        // horloge reculee ou avancee de plusieurs jours.
        val minutes = minutesEcoulees?.coerceIn(
            0L, IslandTimeEngine.MAX_RATTRAPAGE_MINUTES
        ) ?: IslandTimeEngine.minutesDepuisDerniereVisite(
            parcelles.map {
                it.dernierCalculMillis.takeIf { v -> v > 0 } ?: it.planteeMillis
            },
            maintenant
        )

        val etatJardin = db.gardenDao().etat()
        val eau = etatJardin?.eau ?: 0

        val vues = parcelles.map { parcelle ->
            val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
            val croissance = graine?.let {
                CropGrowthEngine.etat(
                    seed = it,
                    sol = SoilType.parId(parcelle.solId),
                    minutesCumulees = parcelle.minutesCumulees,
                    minutesDepuisArrosage =
                        ((maintenant - parcelle.dernierArrosageMillis) / 60_000L)
                            .coerceAtLeast(0L)
                )
            }
            IslandMimoEngine.Vue(
                cle = parcelle.cle,
                aSoif = croissance?.besoinEau ?: false,
                prete = croissance?.prete ?: false
            )
        }

        // L'Atelier releve le plafond d'actions : c'est sa seule fonction,
        // et elle doit passer jusqu'ici sous peine d'etre decorative.
        val batis = batimentsEnService(maintenant)
        val plan = IslandMimoEngine.planifier(
            types, minutes, vues, eau,
            plafond = IslandMimoEngine.plafond(
                aAtelier = IslandBuildingEngine.Type.ATELIER.id in batis
            )
        )
        if (plan.vide) return null

        // Recolte d'abord : le plan a ete construit dans cet ordre, et
        // l'appliquer autrement arroserait des parcelles deja videes.
        plan.aRecolter.forEach { cle ->
            val parcelle = dao.parcelle(cle) ?: return@forEach
            val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
                ?: return@forEach
            if (dao.recolterSiPrete(cle) == 0) return@forEach

            val batiments = batimentsEnService(maintenant)
            val range = IslandStockEngine.ranger(
                graine = graine, quantite = 1,
                stockActuel = dao.totalStock(),
                capacite = IslandStockEngine.capacite(
                    aDepot = IslandBuildingEngine.Type.DEPOT.id in batiments
                ),
                aBoutique = IslandBuildingEngine.Type.BOUTIQUE.id in batiments
            )
            if (range.entrepose > 0) {
                dao.creerStockSiAbsent(IslandStockEntity(graine.id, 0))
                dao.ajouterAuStock(graine.id, range.entrepose)
            }
            if (range.pieces > 0) userRepo.addCoins(range.pieces)
        }

        plan.aArroser.forEach { cle ->
            // Chaque arrosage porte sa propre condition de solde : le plan a
            // borne le total, mais c'est le debit qui fait foi.
            if (db.gardenDao().depenserEauSiAssez(EAU_PAR_ARROSAGE) == 0) return@forEach
            if (dao.arroserSiCulture(cle, maintenant) == 0) {
                db.gardenDao().crediterEau(EAU_PAR_ARROSAGE)
            }
        }

        return IslandMimoEngine.resume(plan)
    }

    /**
     * Chemin d'ouverture atomique : croissance et Mimos consomment le meme
     * intervalle, puis les curseurs sont valides ensemble.
     */
    suspend fun synchroniserCulturesEtMimos(
        maintenant: Long = System.currentTimeMillis()
    ): String? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val minutes = rafraichirCulturesDansTransaction(maintenant)
            travailDesMimosDansTransaction(maintenant, minutes)
        }
    }

    /** Eau restante, pour l'affichage. */
    fun observerEau() = db.gardenDao().observerEau()

    /**
     * Récolte une culture mûre et crédite le joueur.
     *
     * L'ordre compte : la parcelle est vidée **avant** le crédit, et sous
     * condition d'état. Créditer d'abord laisserait deux appuis rapprochés
     * payer deux fois pour une seule récolte.
     */
    suspend fun recolter(x: Int, y: Int): Geste = agir(x, y) { parcelle ->
        val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
            ?: return@agir Geste.Refuse("Il n'y a rien à récolter.")
        if (dao.recolterSiPrete(parcelle.cle) == 0) {
            return@agir Geste.Refuse("Cette culture n'est pas encore prête.")
        }

        val batiments = batimentsEnService(System.currentTimeMillis())
        val depot = IslandStockEngine.ranger(
            graine = graine,
            quantite = 1,
            stockActuel = dao.totalStock(),
            capacite = IslandStockEngine.capacite(
                aDepot = IslandBuildingEngine.Type.DEPOT.id in batiments
            ),
            aBoutique = IslandBuildingEngine.Type.BOUTIQUE.id in batiments
        )

        if (depot.entrepose > 0) {
            dao.creerStockSiAbsent(IslandStockEntity(graine.id, 0))
            dao.ajouterAuStock(graine.id, depot.entrepose)
        }
        // Le surplus est vendu sur place plutot que perdu : une recolte
        // detruite punirait quelqu'un qui a seme, arrose et attendu.
        if (depot.pieces > 0) userRepo.addCoins(depot.pieces)

        Geste.Fait(IslandStockEngine.resumeRecolte(graine, depot))
    }

    /**
     * Recolte plusieurs parcelles en une fois.
     *
     * Chaque parcelle garde sa propre condition de maturite : `recolterSiPrete`
     * rend zero ligne si la plante n'est plus prete, et on passe. Une boucle
     * qui verifierait d'abord puis ecrirait ensuite pourrait crediter une
     * recolte qu'un Mimo vient de prendre entre les deux.
     *
     * Le tout dans une seule transaction : une recolte groupee a moitie
     * appliquee laisserait des parcelles videes sans stock credite, et personne
     * ne saurait lesquelles.
     */
    suspend fun recolterPlusieurs(cles: List<Int>): Geste = withContext(Dispatchers.IO) {
        if (cles.isEmpty()) return@withContext Geste.Refuse("Rien à récolter.")

        db.withTransaction {
            val maintenant = System.currentTimeMillis()
            val batiments = batimentsEnService(maintenant)
            val capacite = IslandStockEngine.capacite(
                aDepot = IslandBuildingEngine.Type.DEPOT.id in batiments
            )
            val aBoutique = IslandBuildingEngine.Type.BOUTIQUE.id in batiments

            var recoltees = 0
            var pieces = 0
            var nom = ""

            cles.forEach { cle ->
                val parcelle = dao.parcelle(cle) ?: return@forEach
                val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
                    ?: return@forEach
                if (dao.recolterSiPrete(cle) == 0) return@forEach

                val range = IslandStockEngine.ranger(
                    graine = graine, quantite = 1,
                    stockActuel = dao.totalStock(),
                    capacite = capacite, aBoutique = aBoutique
                )
                if (range.entrepose > 0) {
                    dao.creerStockSiAbsent(IslandStockEntity(graine.id, 0))
                    dao.ajouterAuStock(graine.id, range.entrepose)
                }
                pieces += range.pieces
                recoltees++
                nom = graine.nom
            }

            if (recoltees == 0) {
                return@withTransaction Geste.Refuse("Ces cultures ne sont plus prêtes.")
            }
            // Le surplus est vendu sur place plutot que perdu : une recolte
            // detruite punirait quelqu'un qui a seme, arrose et attendu.
            if (pieces > 0) userRepo.addCoins(pieces)

            val resume = com.sankailife.core.island.domain.RecolteRapideEngine
                .resume(nom, recoltees)
            Geste.Fait(if (pieces > 0) "$resume — +$pieces 🪙" else resume)
        }
    }

    /**
     * Vend une partie du stock.
     *
     * Le retrait vient d'abord et porte sa propre condition de quantite : c'est
     * lui qui empeche deux ventes concurrentes de vider deux fois le meme lot
     * et de crediter deux fois.
     */
    suspend fun vendre(graineId: String, quantite: Int): Geste = withContext(Dispatchers.IO) {
        db.withTransaction {
            val graine = ALL_SEEDS.firstOrNull { it.id == graineId }
                ?: return@withTransaction Geste.Refuse("Recolte inconnue.")
            if (quantite <= 0) return@withTransaction Geste.Refuse("Rien a vendre.")

            if (dao.retirerDuStock(graineId, quantite) == 0) {
                return@withTransaction Geste.Refuse("Tu n'en as pas autant en stock.")
            }

            val types = batimentsEnService(System.currentTimeMillis())
            val gain = IslandStockEngine.valeurTotale(
                graine, quantite,
                aBoutique = IslandBuildingEngine.Type.BOUTIQUE.id in types,
                aPort = IslandBuildingEngine.Type.PORT.id in types
            )
            userRepo.addCoins(gain)
            Geste.Fait("+$gain 🪙 — $quantite ${graine.nom} vendue(s).")
        }
    }

    fun observerStock() = dao.observerStock()

    /**
     * Cadre commun aux gestes agricoles.
     *
     * Relit la parcelle en base et exécute dans une transaction. Chaque geste
     * s'appuie ensuite sur une écriture conditionnelle : c'est elle, et non le
     * contrôle préalable, qui garantit qu'un double appui ne fait pas deux fois
     * le travail.
     */
    private suspend fun agir(
        x: Int,
        y: Int,
        bloc: suspend (IslandSlotEntity) -> Geste
    ): Geste = withContext(Dispatchers.IO) {
        db.withTransaction {
            val ligne = dao.ile() ?: return@withTransaction Geste.Refuse("Aucune île.")
            if (x !in 0 until ligne.largeur || y !in 0 until ligne.hauteur) {
                return@withTransaction Geste.Refuse("Cette case n'est pas sur l'île.")
            }
            val parcelle = dao.parcelle(y * ligne.largeur + x)
                ?: return@withTransaction Geste.Refuse("Cette parcelle ne t'appartient pas.")
            bloc(parcelle)
        }
    }

    // ------------------------------------------------------------------
    // Bâtiments
    // ------------------------------------------------------------------

    /**
     * Pose un bâtiment.
     *
     * L'accessibilité est vérifiée en plus du verdict du moteur : c'est une
     * propriété du voisinage, pas de l'emplacement. Sans elle, on peut bâtir
     * une Boutique sur un îlot et ne plus jamais y entrer.
     *
     * Le débit passe avant l'écriture, sous condition de solde, et le bâtiment
     * est inséré en `REPLACE` sur un type unique — deux appuis rapprochés ne
     * peuvent donc pas produire deux Boutiques.
     */
    suspend fun batir(type: IslandBuildingEngine.Type, x: Int, y: Int): Geste =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val ligne = dao.ile() ?: return@withTransaction Geste.Refuse("Aucune île.")
                val ile = rehydrater(ligne)
                    ?: return@withTransaction Geste.Refuse("L'île enregistrée est illisible.")
                val utilisateur = db.userDao().getUserOnce()
                    ?: return@withTransaction Geste.Refuse("Profil introuvable.")

                val batiments = dao.batiments()
                val parcelles = dao.parcelles().associateBy { it.cle }
                val prises = batiments.flatMap { b ->
                    IslandBuildingEngine.Type.parId(b.type)?.let {
                        IslandBuildingEngine.casesOccupees(it, b.origineX, b.origineY)
                    }.orEmpty()
                }.toSet()

                val verdict = IslandBuildingEngine.peutBatir(
                    type = type, x = x, y = y,
                    niveauJoueur = utilisateur.level,
                    pieces = utilisateur.coins,
                    dejaConstruit = batiments.any { it.type == type.id },
                    terrainDe = { cx, cy ->
                        if (cx !in 0 until ile.largeur || cy !in 0 until ile.hauteur) null
                        else ile.type(cx, cy)
                    },
                    occupee = { cx, cy ->
                        (cx to cy) in prises ||
                            parcelles[cy * ile.largeur + cx]?.graineId?.isNotBlank() == true
                    }
                )
                if (verdict is IslandBuildingEngine.Verdict.Non) {
                    return@withTransaction Geste.Refuse(verdict.raison)
                }

                val sousArbres = casesSousArbres(ile)
                if (IslandBuildingEngine.casesOccupees(type, x, y).any { it in sousArbres }) {
                    return@withTransaction Geste.Refuse("Un arbre occupe cet emplacement.")
                }

                val accessible = IslandBuildingEngine.accessible(type, x, y) { cx, cy ->
                    cx in 0 until ile.largeur && cy in 0 until ile.hauteur &&
                        ile.type(cx, cy).franchissable
                }
                if (!accessible) {
                    return@withTransaction Geste.Refuse(
                        "${type.libelle} serait inaccessible : il faut un accès à pied."
                    )
                }

                val prix = (verdict as IslandBuildingEngine.Verdict.Oui).prix
                if (prix > 0 && !userRepo.spendCoins(prix)) {
                    return@withTransaction Geste.Refuse("Il te manque des pièces.")
                }

                // Les parcelles nues situées sous l'emprise sont reprises, et
                // ce qui avait été payé pour elles est rendu.
                //
                // Les laisser en place produisait le défaut signalé : le sol
                // acheté restait dessiné sous le bâtiment, et la case gardait
                // son ancien état. Refuser de bâtir aurait été plus simple,
                // mais forcerait à choisir l'emplacement avant d'avoir compris
                // à quoi sert un bâtiment. Rembourser ne fait perdre à personne
                // ce qu'il a payé — le champ `prixPaye` existe pour ça.
                //
                // Une parcelle cultivée, elle, a déjà bloqué la construction
                // plus haut : on ne détruit jamais une culture en cours.
                var rendu = 0
                IslandBuildingEngine.casesOccupees(type, x, y).forEach { (cx, cy) ->
                    val parcelle = parcelles[cy * ile.largeur + cx] ?: return@forEach
                    rendu += parcelle.prixPaye
                    dao.supprimerParcelle(parcelle)
                }
                if (rendu > 0) userRepo.refundCoins(rendu)

                val maintenant = System.currentTimeMillis()
                dao.poserBatiment(
                    IslandBuildingEntity(
                        type = type.id, origineX = x, origineY = y,
                        orientation = 0, niveau = 1,
                        chantierFinMillis = maintenant + type.chantierMinutes * 60_000L
                    )
                )
                val mention = if (rendu > 0) " ${rendu} 🪙 de parcelles rendus." else ""
                Geste.Fait(
                    "${type.emoji} Chantier ouvert — ${type.libelle} prête dans " +
                        "${type.chantierMinutes} min.$mention"
                )
            }
        }

    /**
     * Cases rendues inutilisables par les arbres.
     *
     * Pas seulement celles où pousse un arbre : aussi celles que sa couronne
     * recouvre. Sans ce calcul, une case entièrement cachée par du feuillage
     * restait achetable et cultivable — on pouvait y semer et ne jamais voir ce
     * qui y poussait.
     *
     * Le découpage vient du même moteur que le rendu, avec la même géométrie :
     * deux calculs séparés finiraient par bloquer une autre case que celle
     * qu'on voit cachée.
     */
    private fun casesSousArbres(ile: IslandGenerator.Ile): Set<Pair<Int, Int>> {
        val arbres = IslandForetEngine.decouper(ile.largeur, ile.hauteur) { x, y ->
            ile.type(x, y) == IslandTileType.FOREST
        }
        return IslandForetEngine.casesReservees(arbres)
    }

    /**
     * Types de bâtiments **en service**.
     *
     * Un chantier en cours ne rend aucun service : sans ce filtre, poser une
     * Boutique donnerait son bonus de prix immédiatement, et les étapes de
     * construction ne seraient qu'une animation.
     */
    private suspend fun batimentsEnService(maintenant: Long): Set<String> =
        dao.batiments()
            .filter { IslandBuildingEngine.enService(it.chantierFinMillis, maintenant) }
            .map { it.type }
            .toSet()

    /** Une case est-elle sous l'emprise d'un bâtiment ? */
    private fun occupe(batiment: IslandBuildingEntity, x: Int, y: Int): Boolean {
        // L'emprise se déduit du type, jamais de valeurs écrites en dur ici :
        // le jour où un bâtiment change de taille, il n'y a qu'un endroit à
        // corriger.
        val type = IslandBuildingEngine.Type.parId(batiment.type) ?: return false
        return (x to y) in IslandBuildingEngine
            .casesOccupees(type, batiment.origineX, batiment.origineY).toSet()
    }

    fun observerIle() = dao.observerIle()
    fun observerParcelles() = dao.observerParcelles()
    fun observerBatiments() = dao.observerBatiments()
    fun compterParcelles() = dao.compterParcelles()

    /**
     * Efface l'île et tout ce qui a été posé dessus.
     *
     * N'est appelée que sur demande explicite du joueur. Jamais par une
     * migration, jamais au démarrage.
     */
    suspend fun regenerer(graine: Long): IslandGenerator.Ile = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.effacerBatiments()
            dao.effacerParcelles()
            dao.effacerIle()
        }
        creerSiAbsente(graine)
    }
}
