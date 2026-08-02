package com.sankailife.core.modules

import java.util.Locale

/**
 * Le catalogue des modules téléchargeables.
 *
 * **Rien n'est embarqué dans l'application.** Un catalogue de contenus pesant
 * chacun quelques dizaines de kilo-octets ferait grossir l'installation pour
 * des cours que la plupart n'ouvriront jamais. On télécharge ce dont on a
 * besoin, et ce qui est téléchargé fonctionne ensuite entièrement hors ligne —
 * les cartes entrent dans la base, il n'y a plus rien à aller chercher.
 *
 * Ce moteur ne connaît ni le réseau ni la base : il lit, valide et classe. Tout
 * ce qui peut être décidé sans connexion l'est ici, donc testable sans.
 */
object CatalogueEngine {

    /** Version de format comprise. Au-delà, on refuse plutôt que de deviner. */
    const val VERSION_SCHEMA = 1

    /**
     * Plafond de taille d'un module du catalogue.
     *
     * Les modules réels pèsent un à deux kilo-octets. Deux mégaoctets laissent
     * une marge considérable tout en refusant net une entrée aberrante, qui
     * signalerait un catalogue corrompu plutôt qu'un cours volumineux.
     */
    const val MAX_OCTETS = 2 * 1024 * 1024L

    /** Une entrée du catalogue. */
    data class Entree(
        val id: String,
        val nom: String,
        val description: String = "",
        /** Code BCP-47, vide pour un module qui n'est pas une langue. */
        val langue: String = "",
        val niveau: String = "",
        val cartes: Int = 0,
        val octets: Long = 0L,
        /** Empreinte SHA-256 du paquet, en minuscules. */
        val empreinte: String = "",
        val licence: String = "",
        val auteur: String = "",
        val url: String = ""
    ) {
        /** Taille lisible, pour annoncer le téléchargement avant de le lancer. */
        val taille: String
            get() = if (octets < 1024) "$octets o"
            else String.format(Locale.FRANCE, "%.0f ko", octets / 1024.0)

        /** Sous-titre : ce qui distingue ce module des autres. */
        val details: String
            get() = buildList {
                if (niveau.isNotBlank()) add(niveau)
                add("$cartes cartes")
                add(taille)
            }.joinToString(" · ")
    }

    /**
     * Ce qui empêche d'installer une entrée, ou `null` si rien ne l'empêche.
     *
     * On refuse **avant** de télécharger : faire descendre deux mégaoctets pour
     * ensuite annoncer que l'entrée est invalide gaspille la connexion de
     * quelqu'un qui n'a peut-être que des données mobiles.
     */
    fun refus(entree: Entree): String? = when {
        entree.id.isBlank() -> "Module sans identifiant."
        entree.nom.isBlank() -> "Module sans nom."
        !entree.url.startsWith("https://") ->
            "Adresse non sécurisée : le module ne sera pas téléchargé."
        entree.octets <= 0L -> "Taille inconnue."
        entree.octets > MAX_OCTETS ->
            "Module démesuré (${entree.taille}) : catalogue probablement corrompu."
        entree.empreinte.length != 64 ->
            "Empreinte absente : impossible de vérifier ce qui sera reçu."
        entree.cartes <= 0 -> "Module sans carte."
        else -> null
    }

    /**
     * Vérifie qu'un fichier reçu est bien celui annoncé.
     *
     * Sans cette vérification, un téléchargement interrompu s'installerait à
     * moitié : la moitié des cartes d'un cours, sans qu'aucun message ne le
     * dise, et l'apprenant chercherait pourquoi son module a des trous.
     */
    fun verifierRecu(entree: Entree, octetsRecus: Long, empreinteRecue: String): String? = when {
        octetsRecus != entree.octets ->
            "Téléchargement incomplet : ${octetsRecus} o reçus sur ${entree.octets}."
        !empreinteRecue.equals(entree.empreinte, ignoreCase = true) ->
            "Le fichier reçu ne correspond pas à celui annoncé."
        else -> null
    }

    /** Regroupement d'affichage : les langues d'abord, le reste ensuite. */
    enum class Famille(val libelle: String) {
        LANGUES("Langues"),
        AUTRES("Autres matières")
    }

    fun famille(entree: Entree): Famille =
        if (entree.langue.isNotBlank()) Famille.LANGUES else Famille.AUTRES

    /**
     * Classe le catalogue pour l'affichage.
     *
     * Les entrées refusées sont écartées ici, une fois : les laisser passer
     * obligerait chaque écran à refaire la vérification, et l'un d'eux
     * finirait par l'oublier.
     */
    fun classer(entrees: List<Entree>): List<Pair<Famille, List<Entree>>> = entrees
        .filter { refus(it) == null }
        .sortedWith(compareBy({ famille(it).ordinal }, { it.nom }))
        .groupBy(::famille)
        .toList()
        .sortedBy { it.first.ordinal }

    /**
     * Ce module est-il déjà installé ?
     *
     * Comparé sur le nom, parce que c'est ce que l'installation conserve — le
     * profil Mémo créé porte le nom du module, pas son identifiant.
     *
     * Un module installé reste affiché plutôt que caché : le masquer ferait
     * croire qu'il a disparu du catalogue, et empêcherait de le réinstaller
     * après une suppression par erreur.
     */
    fun estInstalle(entree: Entree, nomsInstalles: Set<String>): Boolean {
        val cible = entree.nom.trim().lowercase()
        return nomsInstalles.any { it.trim().lowercase() == cible }
    }
}
