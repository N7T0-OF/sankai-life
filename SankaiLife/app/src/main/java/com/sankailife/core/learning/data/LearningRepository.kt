package com.sankailife.core.learning.data

import com.sankailife.core.data.db.SankaiDatabase
import com.sankailife.core.domain.engine.FlashcardEngine
import com.sankailife.core.learning.domain.AcademieEngine
import com.sankailife.core.learning.domain.SessionPlanEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Accès aux modules d'apprentissage.
 *
 * **Le contenu n'est jamais copié.** Un module désigne un profil Mémo ; les
 * cartes restent dans `memo_line` et leur état de révision reste celui des
 * boîtes de Leitner déjà en place. Ce dépôt ne fait que lire ce contenu et le
 * mettre en forme pour les moteurs purs, qui décident.
 *
 * C'est ce qui garantit qu'aucune donnée n'est perdue par la refonte : il n'y a
 * rien à migrer, puisqu'il n'y a rien à déplacer.
 */
class LearningRepository(
    private val db: SankaiDatabase
) {
    private val dao = db.learningDao()
    private val memo = db.memoDao()

    /** Fenêtre au-delà de laquelle une faute ne dit plus rien de ce qu'on sait. */
    private val FENETRE_ERREURS_MS = TimeUnit.DAYS.toMillis(14)

    /** Au-delà, la table d'erreurs garderait des traces sans usage. */
    private val RETENTION_ERREURS_MS = TimeUnit.DAYS.toMillis(90)

    fun observerModules(): Flow<List<LearningModuleEntity>> = dao.observerModules()

    /**
     * Récupère le module d'un profil Mémo, en le créant à la première demande.
     *
     * Aucune migration de masse au démarrage : un profil devient un module
     * quand on l'ouvre dans l'Académie, pas avant. Transformer d'office les
     * profils existants créerait des modules pour des listes de courses et des
     * pense-bêtes, qui n'ont rien à faire dans un parcours.
     *
     * Le profil d'origine n'est jamais supprimé ni modifié.
     */
    suspend fun moduleDuProfil(profileId: Long): LearningModuleEntity? =
        withContext(Dispatchers.IO) {
            dao.moduleDuProfil(profileId)?.let { return@withContext it }

            val profil = memo.getProfile(profileId) ?: return@withContext null
            val id = dao.enregistrer(
                LearningModuleEntity(
                    memoProfileId = profileId,
                    nom = profil.nom(),
                    langue = profil.langue,
                    creeMillis = System.currentTimeMillis(),
                    ordre = dao.prochainOrdre()
                )
            )
            dao.module(id)
        }

    /** Le nom affichable d'un profil, jamais vide. */
    private fun com.sankailife.core.data.db.entities.MemoProfileEntity.nom(): String =
        name.ifBlank { "Module sans nom" }

    /**
     * Le parcours d'un module : ses unités et leur état.
     *
     * La maîtrise vient des boîtes de Leitner, seule source de vérité sur ce
     * qui est su. Stocker une progression à côté créerait une seconde valeur
     * qui finirait par contredire la première.
     */
    suspend fun parcours(module: LearningModuleEntity): List<AcademieEngine.Noeud> =
        withContext(Dispatchers.IO) {
            val lignes = memo.getLinesOnce(module.memoProfileId)
            if (lignes.isEmpty()) return@withContext emptyList()

            val unites = AcademieEngine.decouper(
                cartes = lignes.map {
                    AcademieEngine.Carte(
                        id = it.id,
                        ordre = it.orderIndex,
                        boite = it.box,
                        revisions = it.reviewCount
                    )
                },
                titreModule = ""
            )
            AcademieEngine.parcours(
                unites = unites,
                maitrisees = lignes.filter { it.box >= AcademieEngine.BOITE_MAITRISEE }
                    .map { it.id }.toSet(),
                // « Vue » et « maîtrisée » sont deux choses : une carte croisée
                // une fois ouvre la suite du parcours sans être acquise.
                vues = lignes.filter { it.reviewCount > 0 }.map { it.id }.toSet()
            )
        }

    /**
     * Compose la prochaine session d'une unité.
     *
     * @return `null` si l'unité n'existe pas — un identifiant périmé après une
     *   modification du contenu ne doit pas produire une session vide qu'on
     *   lancerait quand même.
     */
    suspend fun preparerSession(
        module: LearningModuleEntity,
        uniteId: String,
        minutes: Int = module.minutesParJour,
        maintenant: Long = System.currentTimeMillis()
    ): SessionPlanEngine.Plan? = withContext(Dispatchers.IO) {
        val lignes = memo.getLinesOnce(module.memoProfileId)
        if (lignes.isEmpty()) return@withContext null

        val unites = AcademieEngine.decouper(
            lignes.map { AcademieEngine.Carte(it.id, it.orderIndex, it.box, it.reviewCount) }
        )
        val unite = unites.firstOrNull { it.id == uniteId } ?: return@withContext null

        val enErreur = dao.cartesEnErreur(maintenant - FENETRE_ERREURS_MS).toSet()
        val parId = lignes.associateBy { it.id }

        val cartes = unite.cartes.mapNotNull { id ->
            val ligne = parId[id] ?: return@mapNotNull null
            val (_, verso) = FlashcardEngine.decouper(ligne.text)
            SessionPlanEngine.Carte(
                id = ligne.id,
                aVerso = verso != null,
                boite = ligne.box,
                enErreur = id in enErreur,
                due = ligne.nextReviewAtMillis <= maintenant
            )
        }

        SessionPlanEngine.composer(
            moduleId = module.id,
            uniteId = uniteId,
            cartes = cartes,
            minutes = minutes,
            typesRecents = typesRecents(module.id),
            // La graine dépend du jour et de l'unité : la session est stable
            // tant qu'on la regarde, et différente le lendemain.
            graine = maintenant / TimeUnit.DAYS.toMillis(1) * 31 + uniteId.hashCode()
        )
    }

    /** Types joués lors des dernières sessions, du plus récent au plus ancien. */
    private suspend fun typesRecents(moduleId: Long): List<SessionPlanEngine.Type> =
        dao.dernieresSessions(moduleId)
            .flatMap { it.typesJoues.split(',') }
            .mapNotNull { nom ->
                SessionPlanEngine.Type.entries.firstOrNull { it.name == nom.trim() }
            }
            .distinct()

    // --- Écriture ------------------------------------------------------------

    suspend fun ouvrirSession(moduleId: Long, uniteId: String): Long =
        withContext(Dispatchers.IO) {
            dao.ouvrirSession(
                LearningSessionEntity(
                    moduleId = moduleId,
                    uniteId = uniteId,
                    debutMillis = System.currentTimeMillis()
                )
            )
        }

    /**
     * Clôture une session.
     *
     * Une session sans aucun exercice fait n'est pas enregistrée comme
     * terminée : ouvrir l'écran puis en sortir ne doit pas remplir la
     * régularité de la semaine.
     */
    suspend fun cloturerSession(
        id: Long,
        faits: Int,
        reussis: Int,
        types: List<SessionPlanEngine.Type>
    ) = withContext(Dispatchers.IO) {
        dao.cloturerSession(
            id = id,
            finMillis = if (faits > 0) System.currentTimeMillis() else 0L,
            faits = faits,
            reussis = reussis,
            types = types.joinToString(",") { it.name }
        )
    }

    suspend fun noterErreur(
        moduleId: Long,
        carteId: Long,
        type: SessionPlanEngine.Type,
        reponseDonnee: String = ""
    ) = withContext(Dispatchers.IO) {
        dao.noterErreur(
            LearningErrorEntity(
                carteId = carteId,
                moduleId = moduleId,
                typeExercice = type.name,
                // Sur un QCM, savoir qu'on a coché la case 2 n'apprend rien ;
                // sur une saisie, la faute exacte est l'information.
                reponseDonnee = if (type.production) reponseDonnee else "",
                momentMillis = System.currentTimeMillis()
            )
        )
    }

    /** Une carte enfin réussie n'est plus une erreur. */
    suspend fun oublierErreurs(carteId: Long) = withContext(Dispatchers.IO) {
        dao.oublierErreurs(carteId)
    }

    /**
     * Ménage, à l'ouverture de l'Académie.
     *
     * Ici plutôt que dans un service : rien ne doit tourner quand
     * l'application est fermée, et une purge n'est pas urgente.
     */
    suspend fun purger(maintenant: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            dao.purgerErreurs(maintenant - RETENTION_ERREURS_MS)
        }

    /** Jours distincts travaillés depuis une date, dans le fuseau de l'appareil. */
    fun joursActifs(depuisMillis: Long): Flow<Int> {
        val decalage = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())
        return dao.joursActifs(depuisMillis, decalage.toLong())
    }
}
