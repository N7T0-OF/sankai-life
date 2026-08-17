package com.sankailife.core.culture

/** Statut juridique explicite d'un contenu culturel. */
enum class ContentRightsStatus {
    PUBLIC_DOMAIN,
    CREATIVE_COMMONS,
    LICENSED,
    /** Seules les informations bibliographiques sont distribuées. */
    METADATA_ONLY
}

/** Formats compris par le coeur de Sankai Life. */
enum class CultureEntryType {
    POEM,
    QUOTE,
    PROVERB,
    ARTWORK,
    HISTORY,
    SCIENCE,
    WORD,
    BIOGRAPHY
}

/**
 * Effort de lecture estimé, utilisé uniquement pour varier les capsules.
 * Il ne produit ni score, ni niveau, ni récompense.
 */
enum class CultureDifficulty { LIGHT, STANDARD, DEEP }

/**
 * Une capsule culturelle autonome et lisible hors connexion.
 *
 * [body] reste nul pour [ContentRightsStatus.METADATA_ONLY]. Les chemins de
 * médias sont relatifs au paquet et ne sont jamais interprétés comme des URL
 * ou comme du code.
 */
data class DailyCultureEntry(
    val id: String,
    val type: CultureEntryType,
    val title: String,
    val body: String?,
    val author: String?,
    val authorBirthYear: Int?,
    val authorDeathYear: Int?,
    val workDate: String?,
    val publicationDate: String?,
    val countryCode: String?,
    val languageCode: String,
    val context: String?,
    val sourceLabel: String?,
    val sourceUrl: String?,
    val rightsStatus: ContentRightsStatus,
    val license: String?,
    val tags: List<String>,
    val difficulty: CultureDifficulty = CultureDifficulty.STANDARD,
    val mediaPath: String? = null
)

/** Description versionnée d'un paquet culturel local. */
data class CulturePackManifest(
    val schemaVersion: Int,
    val id: String,
    val version: String,
    val title: String,
    val description: String,
    val languages: Set<String>,
    val license: String,
    val sourceLabel: String,
    val minAppVersionCode: Int,
    val maxAppVersionCode: Int?,
    val entryCount: Int,
    /** Taille décompressée attendue, hors pack.json. */
    val payloadBytes: Long,
    /** Empreinte SHA-256 de chaque fichier, sauf pack.json lui-même. */
    val files: Map<String, String>
)

/** Paquet entièrement contrôlé, prêt à être conservé localement. */
data class ImportedCulturePack(
    val manifest: CulturePackManifest,
    val entries: List<DailyCultureEntry>,
    /** Documents et médias validés, indexés par leur chemin relatif. */
    val files: Map<String, ByteArray>
)
