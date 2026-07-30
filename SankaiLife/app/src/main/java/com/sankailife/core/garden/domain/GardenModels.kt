package com.sankailife.core.garden.domain

/**
 * Type de sol. Catalogue statique : ces définitions ne changent jamais à
 * l'exécution, les mettre en base ajouterait une migration pour rien.
 */
enum class SoilType(
    val id: String,
    val libelle: String,
    val emoji: String,
    /** Multiplicateur de vitesse de croissance. */
    val vitesse: Float,
    /** Consommation d'eau par cycle. */
    val besoinEau: Int,
    val areneRequise: Int
) {
    TERRE("terre", "Terre classique", "🟫", 1.0f, 2, 1),
    RICHE("riche", "Terre riche", "🟤", 1.15f, 2, 2),
    SABLE("sable", "Sable", "🏜️", 0.9f, 1, 2),
    HUMIDE("humide", "Sol humide", "💧", 1.1f, 3, 4),
    NOCTURNE("nocturne", "Terre nocturne", "🌑", 1.2f, 2, 5),
    CRISTALLIN("cristallin", "Sol cristallin", "💎", 1.35f, 3, 7);

    companion object {
        fun parId(id: String): SoilType = entries.firstOrNull { it.id == id } ?: TERRE
    }
}

/** Rareté d'une graine, qui pilote son coût et son rendement. */
enum class SeedRarity(val libelle: String) {
    COMMUNE("Commune"),
    INHABITUELLE("Inhabituelle"),
    RARE("Rare"),
    LEGENDAIRE("Légendaire")
}

/**
 * Soif d'une espèce.
 *
 * C'est ce qui rend le choix des graines réellement stratégique : un cactus
 * n'est pas un tournesol qu'on arrose moins, c'est une plante qui souffre
 * quand on l'arrose trop.
 */
enum class BesoinEau(val libelle: String) {
    FAIBLE("Préfère un sol sec"),
    MOYEN("Préfère un sol humide"),
    FORT("Demande un arrosage régulier")
}

/**
 * Espèce cultivable.
 *
 * [dureeMinutes] est la durée de base, avant modificateur de sol et avant
 * les bonus gagnés par l'apprentissage.
 */
data class Seed(
    val id: String,
    val nom: String,
    val emoji: String,
    val rarete: SeedRarity,
    val solRequis: SoilType,
    val dureeMinutes: Long,
    val prixPieces: Int,
    val rendementPieces: Int,
    val areneRequise: Int,
    val besoinEau: BesoinEau = BesoinEau.MOYEN
)

/**
 * Catalogue du prototype. Volontairement court : mieux vaut trois espèces
 * bien équilibrées que vingt jamais jouées.
 */
val ALL_SEEDS = listOf(
    Seed("tournesol", "Tournesol", "🌻", SeedRarity.COMMUNE, SoilType.TERRE,
        dureeMinutes = 6 * 60, prixPieces = 40, rendementPieces = 70, areneRequise = 1,
        besoinEau = BesoinEau.MOYEN),
    Seed("menthe", "Menthe mémoire", "🌿", SeedRarity.COMMUNE, SoilType.TERRE,
        dureeMinutes = 3 * 60, prixPieces = 25, rendementPieces = 40, areneRequise = 1,
        besoinEau = BesoinEau.FORT),
    Seed("cactus", "Cactus des mots", "🌵", SeedRarity.INHABITUELLE, SoilType.SABLE,
        dureeMinutes = 12 * 60, prixPieces = 80, rendementPieces = 160, areneRequise = 2,
        besoinEau = BesoinEau.FAIBLE),
    Seed("lune", "Lune-fleur", "🌙", SeedRarity.RARE, SoilType.NOCTURNE,
        dureeMinutes = 20 * 60, prixPieces = 200, rendementPieces = 450, areneRequise = 5,
        besoinEau = BesoinEau.FAIBLE),
    Seed("lotus", "Lotus mémoire", "🪷", SeedRarity.LEGENDAIRE, SoilType.CRISTALLIN,
        dureeMinutes = 36 * 60, prixPieces = 600, rendementPieces = 1500, areneRequise = 7,
        besoinEau = BesoinEau.FORT)
)

fun seedParId(id: String): Seed? = ALL_SEEDS.firstOrNull { it.id == id }

/** État d'une parcelle. */
enum class PlotState {
    LOCKED,
    UNCLEARED,
    EMPTY,
    PREPARED,
    PLANTED,
    GROWING,
    NEEDS_CARE,
    READY_TO_HARVEST
}

/** Qualité d'une récolte. Trois niveaux : au-delà, la nuance devient illisible. */
enum class HarvestQuality(val libelle: String, val multiplicateur: Float) {
    NORMALE("Normale", 1.0f),
    FLORISSANTE("Florissante", 1.4f),
    PARFAITE("Parfaite", 2.0f)
}

/** Les cinq étapes visibles d'une culture. */
enum class CropStage(val libelle: String, val emoji: String) {
    GRAINE("Graine", "•"),
    GERME("Germe", "🌱"),
    POUSSE("Pousse", "🌿"),
    JEUNE("Jeune plante", "🪴"),
    MATURE("Mature", "🌸");

    companion object {
        val derniere: CropStage get() = MATURE
    }
}
