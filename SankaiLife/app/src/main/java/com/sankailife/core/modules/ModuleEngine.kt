package com.sankailife.core.modules

/**
 * Validation d'un module d'apprentissage importé.
 *
 * Un module est une archive de **données**, jamais de code. Ni script, ni
 * classe, ni expression évaluée : uniquement du texte, des cartes et des
 * médias. C'est la seule garantie qui permette d'installer ce qu'un inconnu a
 * publié sans lui donner les clés du téléphone.
 *
 * Cette règle n'est pas une intention, elle est structurelle : le format n'a
 * aucun champ exécutable, et l'importateur n'appelle jamais d'interpréteur.
 * Il n'y a rien à désactiver parce qu'il n'y a rien à activer.
 */
object ModuleEngine {

    const val VERSION_SCHEMA = 1

    /** Limites de sécurité, appliquées avant tout écrit en base. */
    const val MAX_CARTES = 20_000
    const val MAX_LONGUEUR_LIGNE = 2_000
    const val MAX_OCTETS = 20 * 1024 * 1024

    /** Ce qu'un module déclare de lui-même. */
    data class Manifeste(
        val schemaVersion: Int = 0,
        val id: String = "",
        val nom: String = "",
        val version: String = "",
        val langue: String = "",
        val langueSource: String = "",
        val auteur: String = "",
        val description: String = "",
        val licence: String = ""
    )

    /** Aperçu montré avant d'installer quoi que ce soit. */
    data class Apercu(
        val manifeste: Manifeste,
        val nombreCartes: Int,
        val octets: Long
    )

    sealed interface Verdict {
        data class Utilisable(val apercu: Apercu, val reserve: String?) : Verdict
        data class Refuse(val raison: String) : Verdict
    }

    /**
     * Contrôle un module avant installation.
     *
     * L'ordre suit le coût de l'erreur : on refuse d'abord ce qui n'est pas un
     * module, puis ce qui est trop récent pour être compris, puis ce qui est
     * démesuré. Vérifier la taille avant le format ferait parler de mégaoctets
     * à propos d'un fichier qui n'est pas un module du tout.
     */
    fun verifier(
        manifeste: Manifeste?,
        nombreCartes: Int,
        octets: Long
    ): Verdict {
        if (manifeste == null || manifeste.id.isBlank()) {
            return Verdict.Refuse("Ce fichier n'est pas un module Sankai Life.")
        }
        if (manifeste.schemaVersion > VERSION_SCHEMA) {
            return Verdict.Refuse(
                "Module créé pour une version plus récente de l'application."
            )
        }
        if (nombreCartes <= 0) {
            return Verdict.Refuse("Ce module ne contient aucune carte.")
        }
        if (nombreCartes > MAX_CARTES) {
            return Verdict.Refuse(
                "Module trop volumineux : $nombreCartes cartes, maximum $MAX_CARTES."
            )
        }
        if (octets > MAX_OCTETS) {
            return Verdict.Refuse("Fichier trop lourd (${octets / 1024 / 1024} Mo).")
        }

        val reserve = when {
            manifeste.auteur.isBlank() ->
                "Ce module ne déclare pas d'auteur."
            manifeste.licence.isBlank() ->
                "Ce module ne déclare pas de licence. Vérifie que tu as le droit de l'utiliser."
            else -> null
        }
        return Verdict.Utilisable(Apercu(manifeste, nombreCartes, octets), reserve)
    }

    /**
     * Chemin interne accepté dans l'archive.
     *
     * Même contrôle que pour les sauvegardes, et pour la même raison : une
     * archive vient de l'extérieur, et une entrée nommée `../../databases/…`
     * écrirait hors du dossier prévu.
     */
    fun cheminSur(nom: String): Boolean =
        nom.isNotBlank() &&
            !nom.startsWith("/") &&
            !nom.contains("..") &&
            !nom.contains("\\") &&
            !nom.contains(":")

    /**
     * Nettoie une ligne de carte.
     *
     * Tronquer plutôt que refuser : une seule ligne aberrante dans un module
     * de mille cartes ne doit pas faire perdre les neuf cent quatre-vingt-dix-
     * neuf autres.
     */
    fun nettoyerLigne(brut: String): String? {
        val ligne = brut.trim().replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        if (ligne.isBlank()) return null
        return ligne.take(MAX_LONGUEUR_LIGNE)
    }

    /**
     * Nom sous lequel installer le module.
     *
     * Un module portant un nom déjà pris devient une copie. Même règle que
     * pour la restauration de sauvegarde : remplacer par erreur détruit un
     * travail, ajouter par erreur crée un doublon qu'on supprime en deux
     * gestes.
     */
    fun nomInstallation(souhaite: String, existants: Set<String>): String {
        val base = souhaite.ifBlank { "Module importé" }
        if (base !in existants) return base

        var n = 2
        while ("$base ($n)" in existants) n++
        return "$base ($n)"
    }
}
