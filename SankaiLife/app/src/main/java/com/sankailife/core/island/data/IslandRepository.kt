package com.sankailife.core.island.data

import androidx.room.withTransaction
import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.data.repository.UserRepository
import com.sankailife.core.garden.domain.ALL_SEEDS
import com.sankailife.core.garden.domain.CropGrowthEngine
import com.sankailife.core.garden.domain.SoilType
import com.sankailife.core.island.domain.IslandCodec
import com.sankailife.core.island.domain.IslandCultureEngine
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
    suspend fun rafraichirCultures(maintenant: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                dao.parcelles().forEach { parcelle ->
                    if (parcelle.graineId.isBlank()) return@forEach
                    val graine = ALL_SEEDS.firstOrNull { it.id == parcelle.graineId }
                        ?: return@forEach
                    val depuis = parcelle.dernierCalculMillis.takeIf { it > 0 }
                        ?: parcelle.planteeMillis
                    if (maintenant <= depuis) return@forEach

                    val ecoulees = (maintenant - depuis) / 60_000L
                    if (ecoulees <= 0L) return@forEach

                    val arrosees = IslandCultureEngine.minutesArrosees(
                        parcelle.dernierArrosageMillis, depuis, maintenant
                    )
                    val acquises = CropGrowthEngine.minutesAcquises(ecoulees, arrosees)
                    val cumulees = parcelle.minutesCumulees + acquises

                    val sol = SoilType.parId(parcelle.solId)
                    val etat = CropGrowthEngine.etat(
                        seed = graine,
                        sol = sol,
                        minutesCumulees = cumulees,
                        minutesDepuisArrosage =
                            (maintenant - parcelle.dernierArrosageMillis) / 60_000L
                    )

                    dao.majParcelle(
                        parcelle.copy(
                            minutesCumulees = cumulees,
                            dernierCalculMillis = maintenant,
                            etat = IslandCultureEngine
                                .etatApres(etat.prete, etat.besoinEau).name
                        )
                    )
                }
            }
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

    /** Arrose une culture. */
    suspend fun arroser(x: Int, y: Int): Geste = agir(x, y) { parcelle ->
        if (dao.arroserSiCulture(parcelle.cle, System.currentTimeMillis()) == 0) {
            Geste.Refuse("Il n'y a rien à arroser.")
        } else {
            Geste.Fait("Parcelle arrosée.")
        }
    }

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
        userRepo.addCoins(graine.rendementPieces)
        Geste.Fait("+${graine.rendementPieces} 🪙 — ${graine.nom} récoltée.")
    }

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

    /** Une case est-elle sous l'emprise d'un bâtiment ? */
    private fun occupe(batiment: IslandBuildingEntity, x: Int, y: Int): Boolean {
        // Emprise 2×2 par défaut, la seule utilisée pour l'instant. Elle sera
        // lue depuis le type de bâtiment quand d'autres tailles existeront.
        return x in batiment.origineX..batiment.origineX + 1 &&
            y in batiment.origineY..batiment.origineY + 1
    }

    fun observerIle() = dao.observerIle()
    fun observerParcelles() = dao.observerParcelles()
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
