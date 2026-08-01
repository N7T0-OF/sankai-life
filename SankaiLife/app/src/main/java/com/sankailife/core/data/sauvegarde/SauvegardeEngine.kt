package com.sankailife.core.data.sauvegarde

import java.security.MessageDigest

/**
 * Règles de la sauvegarde et de la restauration.
 *
 * Tout ce qui peut être décidé sans toucher au disque vit ici : format du
 * manifeste, compatibilité des versions, empreinte, choix de ce qu'on
 * restaure. Le reste — lire, écrire, remplacer — est dans le dépôt.
 *
 * La séparation n'est pas cosmétique. Une restauration écrase des données que
 * personne ne pourra récupérer : les règles qui l'autorisent doivent être
 * testables sans risquer une base réelle.
 */
object SauvegardeEngine {

    /** Version du format de sauvegarde, indépendante de celle de l'app. */
    const val VERSION_FORMAT = 1

    const val EXTENSION = "sankai"

    /** Ce qu'une sauvegarde contient, section par section. */
    enum class Section(val cle: String, val libelle: String) {
        PROFIL("profile", "Profil et progression"),
        REGLAGES("settings", "Paramètres"),
        MEMOS("memos", "Mémos et flash cards"),
        JARDIN("garden", "Jardin, parcelles et Mimos"),
        COFFRES("chests", "Coffres et défis")
    }

    /**
     * En-tête d'une sauvegarde.
     *
     * `appVersionCode` sert à diagnostiquer, jamais à refuser : une sauvegarde
     * faite avec une version plus récente peut très bien être lisible, et
     * bloquer sur ce seul critère priverait quelqu'un de ses données pour une
     * raison qui n'en est pas une.
     */
    data class Manifeste(
        val versionFormat: Int = VERSION_FORMAT,
        val appVersionCode: Int = 0,
        val creeLe: String = "",
        val sections: List<String> = emptyList(),
        val empreinte: String = ""
    )

    /** Verdict porté sur un fichier avant restauration. */
    sealed interface Verdict {
        /** Restaurable, éventuellement avec une réserve à afficher. */
        data class Utilisable(val manifeste: Manifeste, val reserve: String?) : Verdict

        /** Refusé, avec la raison exacte. */
        data class Refuse(val raison: String) : Verdict
    }

    /**
     * Contrôle un fichier avant d'y toucher.
     *
     * L'ordre des vérifications compte : on refuse d'abord ce qui n'est pas
     * une sauvegarde, ensuite ce qui est corrompu, et seulement à la fin on
     * nuance sur la version. Inverser reviendrait à parler de version à propos
     * d'un fichier qui n'en a pas.
     */
    fun verifier(
        manifeste: Manifeste?,
        empreinteCalculee: String,
        versionAppActuelle: Int
    ): Verdict {
        if (manifeste == null) {
            return Verdict.Refuse("Ce fichier n'est pas une sauvegarde Sankai Life.")
        }
        if (manifeste.versionFormat > VERSION_FORMAT) {
            return Verdict.Refuse(
                "Sauvegarde créée par une version plus récente de l'application. " +
                    "Mets à jour Sankai Life pour la restaurer."
            )
        }
        if (manifeste.empreinte.isNotBlank() && manifeste.empreinte != empreinteCalculee) {
            return Verdict.Refuse(
                "Le fichier est abîmé : son empreinte ne correspond pas à son contenu."
            )
        }
        if (manifeste.sections.isEmpty()) {
            return Verdict.Refuse("Cette sauvegarde ne contient aucune donnée.")
        }

        val reserve = when {
            manifeste.appVersionCode > versionAppActuelle ->
                "Cette sauvegarde vient d'une version plus récente. " +
                    "Certaines données récentes pourraient être ignorées."
            manifeste.versionFormat < VERSION_FORMAT ->
                "Ancien format de sauvegarde. La restauration reste possible."
            else -> null
        }
        return Verdict.Utilisable(manifeste, reserve)
    }

    /** Sections réellement restaurables : présentes dans le fichier et demandées. */
    fun sectionsARestaurer(
        presentes: List<String>,
        demandees: Set<Section>
    ): List<Section> = Section.entries
        .filter { it.cle in presentes && it in demandees }

    /**
     * Conduite à tenir sur un doublon.
     *
     * `IGNORER` est le défaut partout où le choix n'est pas explicite :
     * remplacer par erreur détruit un travail, ignorer par erreur ne fait que
     * remettre à plus tard.
     */
    enum class SurDoublon { REMPLACER, FUSIONNER, COPIER, IGNORER }

    /** Empreinte du contenu, pour détecter un fichier abîmé. */
    fun empreinte(contenu: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(contenu)
            .joinToString("") { "%02x".format(it) }

    /**
     * Nom du fichier proposé.
     *
     * La date est en tête pour que le tri alphabétique du sélecteur de
     * fichiers Android donne aussi l'ordre chronologique.
     */
    fun nomFichier(jourIso: String): String = "sankai_backup_$jourIso.$EXTENSION"

    /**
     * Chemin interne accepté dans l'archive.
     *
     * Une archive est un fichier reçu de l'extérieur : une entrée nommée
     * `../../databases/sankai_db` écrirait hors du dossier prévu. Le contrôle
     * est ici, avant toute écriture, et ne dépend d'aucun appelant.
     */
    fun cheminSur(nom: String): Boolean =
        nom.isNotBlank() &&
            !nom.startsWith("/") &&
            !nom.contains("..") &&
            !nom.contains("\\") &&
            !nom.contains(":")
}
