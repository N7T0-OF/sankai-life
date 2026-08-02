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

    /**
     * Version de format comprise. Au-delà, on refuse plutôt que de deviner.
     *
     * La 2 ajoute les collections. Une application plus ancienne refusera ce
     * catalogue au lieu d'en lire la moitié : mieux vaut dire « mets à jour »
     * que d'afficher un catalogue amputé sans l'expliquer.
     */
    const val VERSION_SCHEMA = 2

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
        /** Collection à laquelle il appartient, vide s'il est seul. */
        val collection: String = "",
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
     * Une collection : plusieurs modules qui forment un seul parcours.
     *
     * **C'est ce qui manquait.** Six niveaux de portugais donnaient six thèmes
     * indépendants : on installait A1 sans savoir que A2 existait, et rien ne
     * disait par où continuer une fois A1 terminé.
     *
     * Elle s'installe d'un geste **ou** niveau par niveau, et les deux doivent
     * rester possibles : quelqu'un qui débute n'a pas besoin du C2, et
     * quelqu'un qui révise n'a pas envie de tout reprendre.
     */
    data class Collection(
        val id: String,
        val nom: String,
        val description: String = "",
        val langue: String = "",
        val auteur: String = "",
        /** Identifiants des modules, dans l'ordre d'apprentissage. */
        val modules: List<String> = emptyList(),
        /** Niveaux correspondants, même ordre. Vide pour une matière sans niveau. */
        val niveaux: List<String> = emptyList(),
        val cartes: Int = 0,
        val octets: Long = 0L,
        val empreinte: String = "",
        val url: String = ""
    ) {
        val taille: String
            get() = if (octets < 1024) "$octets o"
            else String.format(Locale.FRANCE, "%.0f ko", octets / 1024.0)

        /** « 6 niveaux · A1 → C2 · 797 cartes · 18 ko ». */
        val details: String
            get() = buildList {
                add("${modules.size} module(s)")
                val bornes = niveaux.filter { it.isNotBlank() }
                if (bornes.size >= 2) add("${bornes.first()} → ${bornes.last()}")
                add("$cartes cartes")
                add(taille)
            }.joinToString(" · ")
    }

    /** Mêmes contrôles que pour un module : refuser avant de télécharger. */
    fun refus(collection: Collection): String? = when {
        collection.id.isBlank() -> "Collection sans identifiant."
        collection.nom.isBlank() -> "Collection sans nom."
        collection.modules.isEmpty() -> "Collection vide."
        !collection.url.startsWith("https://") ->
            "Adresse non sécurisée : la collection ne sera pas téléchargée."
        collection.octets <= 0L -> "Taille inconnue."
        collection.octets > MAX_OCTETS ->
            "Collection démesurée (${collection.taille})."
        collection.empreinte.length != 64 ->
            "Empreinte absente : impossible de vérifier ce qui sera reçu."
        else -> null
    }

    /**
     * Part de la collection déjà installée, de 0 à 1.
     *
     * Sert à proposer « Installer les 4 niveaux restants » plutôt que de
     * reproposer l'ensemble à quelqu'un qui en a déjà la moitié.
     */
    fun partInstallee(
        collection: Collection,
        modulesDuCatalogue: List<Entree>,
        nomsInstalles: Set<String>
    ): Float {
        if (collection.modules.isEmpty()) return 0f
        val parId = modulesDuCatalogue.associateBy { it.id }
        val installes = collection.modules.count { id ->
            parId[id]?.let { estInstalle(it, nomsInstalles) } == true
        }
        return installes.toFloat() / collection.modules.size
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
