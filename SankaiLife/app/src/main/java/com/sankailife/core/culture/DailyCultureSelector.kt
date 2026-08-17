package com.sankailife.core.culture

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

/** Une sélection passée, conservée localement par le profil. */
data class CultureSelectionHistory(
    val entryId: String,
    val selectedOn: LocalDate
)

data class DailyCultureSelectionRequest(
    val profileId: String,
    val localDate: LocalDate,
    /** Version agrégée du catalogue ou du paquet. */
    val packVersion: String,
    val enabledTypes: Set<CultureEntryType> = CultureEntryType.entries.toSet(),
    val enabledLanguages: Set<String> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    val preferredTags: Set<String> = emptySet(),
    /**
     * Types préférés pour le moment de la journée. Préférence douce : si
     * aucun contenu du moment n'est disponible, la sélection retombe sur
     * tout le catalogue pour garantir une découverte par jour.
     */
    val preferredTypes: Set<CultureEntryType> = emptySet(),
    /** Ordre indifférent : les dates déterminent la récence. */
    val history: List<CultureSelectionHistory> = emptyList(),
    val recentWindow: Int = 14
)

/**
 * Choisit une capsule sans réseau et sans flux infini.
 *
 * La graine associe profil, date et version du pack. Les candidats sont triés
 * avant classement : le résultat ne dépend donc ni de l'ordre des fichiers ni
 * de celui d'une requête en base. L'historique du jour courant est ignoré afin
 * qu'enregistrer le choix ne puisse pas en afficher un autre après redémarrage.
 */
object DailyCultureSelector {

    fun select(
        entries: Collection<DailyCultureEntry>,
        request: DailyCultureSelectionRequest
    ): DailyCultureEntry? {
        require(request.profileId.isNotBlank()) { "Le profil est obligatoire." }
        require(request.packVersion.isNotBlank()) { "La version du catalogue est obligatoire." }
        require(request.recentWindow >= 0) { "La fenêtre de répétition ne peut pas être négative." }

        val filtered = entries.asSequence()
            .filter { it.type in request.enabledTypes }
            .filter { request.enabledLanguages.isEmpty() || it.languageCode in request.enabledLanguages }
            .toList()
        require(filtered.map { it.id }.distinct().size == filtered.size) {
            "Les identifiants de capsules doivent être uniques dans le catalogue."
        }
        // Préférence douce du moment : restreint aux types du moment quand ils
        // existent, sinon retombe sur tout le catalogue. Jamais un filtre dur.
        val eligible = if (request.preferredTypes.isNotEmpty()) {
            val preferes = filtered.filter { it.type in request.preferredTypes }
            if (preferes.isNotEmpty()) preferes else filtered
        } else {
            filtered
        }.sortedBy { it.id }
        if (eligible.isEmpty()) return null

        val byId = eligible.associateBy { it.id }
        val pastHistory = request.history.asSequence()
            .filter { it.selectedOn < request.localDate }
            .sortedByDescending { it.selectedOn }
            .toList()
        val recent = pastHistory.take(request.recentWindow)
        val recentIds = recent.mapTo(linkedSetOf()) { it.entryId }

        // Ne répète pas tant qu'un contenu autorisé reste disponible.
        var candidates = eligible.filterNot { it.id in recentIds }
        val repetitionIsNecessary = candidates.isEmpty()
        if (repetitionIsNecessary) candidates = eligible

        val previous = pastHistory.firstOrNull()?.entryId?.let(byId::get)
        if (previous?.difficulty == CultureDifficulty.DEEP &&
            candidates.any { it.difficulty != CultureDifficulty.DEEP }
        ) {
            candidates = candidates.filter { it.difficulty != CultureDifficulty.DEEP }
        }

        val recentEntries = recent.mapNotNull { byId[it.entryId] }
        val recentCountries = recentEntries.mapNotNull { it.countryCode }
        val recentCenturies = recentEntries.mapNotNull(::century)
        val normalizedPreferredTags = request.preferredTags.mapTo(mutableSetOf()) {
            it.lowercase(Locale.ROOT)
        }

        return candidates.maxWithOrNull(
            compareBy<DailyCultureEntry> { entry ->
                var score = deterministicScore(entry.id, request)
                if (entry.id in request.favoriteIds && entry.id !in recentIds) score += 40_000L
                val matchingTags = entry.tags.count {
                    it.lowercase(Locale.ROOT) in normalizedPreferredTags
                }
                score += matchingTags * 15_000L
                score -= recentCountries.count { it == entry.countryCode } * 12_000L
                century(entry)?.let { value ->
                    score -= recentCenturies.count { it == value } * 8_000L
                }
                if (repetitionIsNecessary) {
                    val index = recent.indexOfFirst { it.entryId == entry.id }
                    if (index >= 0) score -= (recent.size - index) * 20_000L
                }
                score
            }.thenByDescending { it.id }
        )
    }

    private fun deterministicScore(
        entryId: String,
        request: DailyCultureSelectionRequest
    ): Long {
        val seed = buildString {
            append(request.profileId)
            append('\u0000')
            append(request.localDate)
            append('\u0000')
            append(request.packVersion)
            append('\u0000')
            append(entryId)
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(StandardCharsets.UTF_8))
        var value = 0L
        repeat(Long.SIZE_BYTES) { index ->
            value = (value shl 8) or (bytes[index].toLong() and 0xffL)
        }
        return (value and Long.MAX_VALUE) % 10_000L
    }

    private fun century(entry: DailyCultureEntry): Int? {
        val year = sequenceOf(entry.workDate, entry.publicationDate)
            .filterNotNull()
            .mapNotNull { raw ->
                val years = Regex("-?\\d{1,4}").findAll(raw)
                    .mapNotNull { it.value.toIntOrNull() }
                    .toList()
                years.firstOrNull { kotlin.math.abs(it) > 31 } ?: years.firstOrNull()
            }
            .firstOrNull()
            ?: return null
        return if (year > 0) (year - 1) / 100 + 1 else year / 100
    }
}
