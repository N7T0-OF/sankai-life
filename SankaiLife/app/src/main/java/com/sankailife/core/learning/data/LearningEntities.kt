package com.sankailife.core.learning.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un module d'apprentissage : une matière complète.
 *
 * **Il enveloppe un profil Mémo, il ne le remplace pas.** Le contenu — les
 * cartes — reste dans `memo_line`, et c'est délibéré : modifier une ligne dans
 * l'éditeur Mémo doit modifier la carte du parcours, parce que c'est la même.
 * Copier le contenu dans une table d'apprentissage créerait deux vérités qui
 * divergeraient au premier changement.
 *
 * Cette table ne porte donc que ce qui n'existe nulle part ailleurs : le niveau
 * visé, l'objectif quotidien, la plante liée.
 */
@Entity(
    tableName = "learning_module",
    indices = [Index("memoProfileId")]
)
data class LearningModuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /** Profil Mémo qui fournit le contenu. 0 tant qu'aucun n'est rattaché. */
    val memoProfileId: Long = 0L,

    val nom: String = "",

    /** Langue du contenu au format BCP-47, vide si ce n'est pas une langue. */
    val langue: String = "",

    /**
     * Niveau visé, au format européen : A1, A2, B1, B2, C1, C2.
     *
     * Vide pour les modules qui ne sont pas des langues — un module de
     * raccourcis Blender n'a pas de niveau européen, et lui en attribuer un
     * serait un habillage sans contenu.
     *
     * Il n'existe pas de niveau A3, et rien ici ne doit en fabriquer.
     */
    val niveau: String = "",

    /** Minutes par jour que l'apprenant s'est fixées. */
    val minutesParJour: Int = 5,

    /**
     * Plante ou zone liée dans l'Île.
     *
     * Vide tant qu'aucune n'est choisie. La plante progresse avec les unités
     * terminées et **ne régresse jamais** sur une erreur : perdre un arbre
     * parce qu'on s'est trompé punirait exactement ce qu'on veut encourager.
     */
    val planteLiee: String = "",

    val creeMillis: Long = 0L,

    /** Ordre d'affichage dans la liste des modules. */
    val ordre: Int = 0
)

/**
 * Une erreur, datée.
 *
 * Aujourd'hui, « Mes erreurs » se déduit des boîtes de Leitner : une carte en
 * boîte basse est supposée mal sue. C'est une approximation utile mais muette —
 * elle ne sait pas **quand** ni **sur quel exercice** la faute est arrivée, donc
 * elle ne peut pas expliquer pourquoi une révision est proposée.
 *
 * Cette table permet de dire « tu as confondu ces deux mots deux fois cette
 * semaine » au lieu de « révise ça ». La différence n'est pas cosmétique : une
 * révision dont on comprend la raison se fait, une révision arbitraire s'évite.
 */
@Entity(
    tableName = "learning_error",
    indices = [Index("carteId"), Index("momentMillis")]
)
data class LearningErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /** Ligne Mémo ratée. */
    val carteId: Long = 0L,

    val moduleId: Long = 0L,

    /** Valeur de `SessionPlanEngine.Type`. */
    val typeExercice: String = "",

    /**
     * Ce qui a été répondu, quand la réponse est une saisie.
     *
     * Vide pour un QCM — enregistrer « la case 2 » n'apprend rien. Sur une
     * saisie, en revanche, la faute exacte est l'information : « caza » pour
     * « casa » est une erreur d'orthographe, « cama » est une confusion de mot.
     */
    val reponseDonnee: String = "",

    val momentMillis: Long = 0L
)

/**
 * Une session terminée.
 *
 * Sert à deux choses concrètes, et à rien d'autre : varier les exercices d'une
 * session à l'autre (il faut savoir ce qui vient d'être joué) et afficher la
 * régularité de la semaine. Pas de statistiques décoratives.
 */
@Entity(
    tableName = "learning_session",
    indices = [Index("moduleId"), Index("finMillis")]
)
data class LearningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    val moduleId: Long = 0L,

    /** Unité travaillée, telle que nommée par `AcademieEngine`. */
    val uniteId: String = "",

    /** Types joués, séparés par des virgules, dans l'ordre. */
    val typesJoues: String = "",

    val exercicesFaits: Int = 0,
    val exercicesReussis: Int = 0,

    val debutMillis: Long = 0L,
    val finMillis: Long = 0L
) {
    /**
     * Une session commencée et abandonnée ne compte pas.
     *
     * Sinon ouvrir l'écran puis en sortir remplirait la régularité de la
     * semaine sans avoir rien appris.
     */
    val terminee: Boolean get() = finMillis > 0L && exercicesFaits > 0
}
