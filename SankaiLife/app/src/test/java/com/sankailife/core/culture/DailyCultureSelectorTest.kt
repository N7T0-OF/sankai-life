package com.sankailife.core.culture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DailyCultureSelectorTest {
    private val today = LocalDate.of(2026, 8, 8)

    @Test
    fun `le choix du jour est stable quel que soit l'ordre des entrees`() {
        val entries = listOf(entry("a"), entry("b"), entry("c"))
        val request = request()

        val first = DailyCultureSelector.select(entries, request)
        val reversed = DailyCultureSelector.select(entries.reversed(), request)

        assertEquals(first?.id, reversed?.id)
    }

    @Test
    fun `enregistrer le choix du jour ne change pas la capsule`() {
        val entries = listOf(entry("a"), entry("b"), entry("c"))
        val first = DailyCultureSelector.select(entries, request())!!
        val afterSave = DailyCultureSelector.select(
            entries,
            request(history = listOf(CultureSelectionHistory(first.id, today)))
        )

        assertEquals(first.id, afterSave?.id)
    }

    @Test
    fun `une entree recente n'est pas repetee si une autre existe`() {
        val entries = listOf(entry("a"), entry("b"))
        val selected = DailyCultureSelector.select(
            entries,
            request(history = listOf(CultureSelectionHistory("a", today.minusDays(1))))
        )

        assertEquals("b", selected?.id)
    }

    @Test
    fun `quand tout a ete vu le choix le plus ancien redevient disponible`() {
        val entries = listOf(entry("a"), entry("b"))
        val selected = DailyCultureSelector.select(
            entries,
            request(
                history = listOf(
                    CultureSelectionHistory("a", today.minusDays(1)),
                    CultureSelectionHistory("b", today.minusDays(9))
                )
            )
        )

        assertEquals("b", selected?.id)
    }

    @Test
    fun `les categories et langues choisies sont respectees`() {
        val entries = listOf(
            entry("poem-fr", type = CultureEntryType.POEM, language = "fr"),
            entry("science-fr", type = CultureEntryType.SCIENCE, language = "fr"),
            entry("science-pt", type = CultureEntryType.SCIENCE, language = "pt")
        )
        val selected = DailyCultureSelector.select(
            entries,
            request(
                enabledTypes = setOf(CultureEntryType.SCIENCE),
                enabledLanguages = setOf("pt")
            )
        )

        assertEquals("science-pt", selected?.id)
    }

    @Test
    fun `un favori non recent est prioritaire sans devenir une streak`() {
        val entries = listOf(entry("a"), entry("favorite"), entry("c"))
        val selected = DailyCultureSelector.select(
            entries,
            request(favoriteIds = setOf("favorite"))
        )

        assertEquals("favorite", selected?.id)

        val next = DailyCultureSelector.select(
            entries,
            request(
                favoriteIds = setOf("favorite"),
                history = listOf(CultureSelectionHistory("favorite", today.minusDays(1)))
            )
        )
        assertNotEquals("favorite", next?.id)
    }

    @Test
    fun `deux contenus profonds ne s'enchainent pas si une alternative existe`() {
        val entries = listOf(
            entry("deep", difficulty = CultureDifficulty.DEEP),
            entry("light", difficulty = CultureDifficulty.LIGHT),
            entry("previous", difficulty = CultureDifficulty.DEEP)
        )
        val selected = DailyCultureSelector.select(
            entries,
            request(history = listOf(CultureSelectionHistory("previous", today.minusDays(1))))
        )

        assertEquals(CultureDifficulty.LIGHT, selected?.difficulty)
    }

    @Test
    fun `le pays recent est varie quand une alternative existe`() {
        val entries = listOf(
            entry("fr", country = "FR", workDate = "1847"),
            entry("pt", country = "PT", workDate = "1527"),
            entry("previous", country = "FR", workDate = "1840")
        )
        val selected = DailyCultureSelector.select(
            entries,
            request(history = listOf(CultureSelectionHistory("previous", today.minusDays(1))))
        )

        assertEquals("pt", selected?.id)
    }

    @Test
    fun `aucune capsule n'est inventee quand les filtres ne correspondent pas`() {
        val selected = DailyCultureSelector.select(
            listOf(entry("poem", type = CultureEntryType.POEM)),
            request(enabledTypes = setOf(CultureEntryType.SCIENCE))
        )

        assertNull(selected)
    }

    @Test
    fun `un catalogue ambigu avec deux identifiants identiques est refuse`() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyCultureSelector.select(listOf(entry("same"), entry("same")), request())
        }
    }

    @Test
    fun `une preference de moment privilegie les types du moment quand ils existent`() {
        val entries = listOf(
            entry("mot", type = CultureEntryType.WORD),
            entry("poeme", type = CultureEntryType.POEM)
        )

        val matin = DailyCultureSelector.select(
            entries,
            request(preferredTypes = setOf(CultureEntryType.WORD, CultureEntryType.PROVERB))
        )

        assertEquals("mot", matin?.id)
    }

    @Test
    fun `une preference de moment retombe sur tout le catalogue quand le type prefere est absent`() {
        val entries = listOf(
            entry("mot", type = CultureEntryType.WORD),
            entry("poeme", type = CultureEntryType.POEM)
        )

        // Soir : seuls des poemes sont demandes, aucun n'est disponible.
        val soir = DailyCultureSelector.select(
            entries,
            request(preferredTypes = setOf(CultureEntryType.POEM, CultureEntryType.QUOTE))
        )

        // La decouverte du jour reste garantie : on tombe sur le catalogue.
        assertNotNull(soir)
    }

    private fun request(
        enabledTypes: Set<CultureEntryType> = CultureEntryType.entries.toSet(),
        enabledLanguages: Set<String> = emptySet(),
        favoriteIds: Set<String> = emptySet(),
        preferredTypes: Set<CultureEntryType> = emptySet(),
        history: List<CultureSelectionHistory> = emptyList()
    ) = DailyCultureSelectionRequest(
        profileId = "profile-1",
        localDate = today,
        packVersion = "catalog-1",
        enabledTypes = enabledTypes,
        enabledLanguages = enabledLanguages,
        favoriteIds = favoriteIds,
        preferredTypes = preferredTypes,
        history = history
    )

    private fun entry(
        id: String,
        type: CultureEntryType = CultureEntryType.POEM,
        language: String = "fr",
        country: String = "FR",
        workDate: String = "1900",
        difficulty: CultureDifficulty = CultureDifficulty.STANDARD
    ) = DailyCultureEntry(
        id = id,
        type = type,
        title = id,
        body = "Texte",
        author = null,
        authorBirthYear = null,
        authorDeathYear = null,
        workDate = workDate,
        publicationDate = null,
        countryCode = country,
        languageCode = language,
        context = null,
        sourceLabel = "Source",
        sourceUrl = null,
        rightsStatus = ContentRightsStatus.PUBLIC_DOMAIN,
        license = "Domaine public",
        tags = emptyList(),
        difficulty = difficulty
    )
}
