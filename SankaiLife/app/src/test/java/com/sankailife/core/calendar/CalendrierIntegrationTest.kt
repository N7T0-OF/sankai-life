package com.sankailife.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendrierIntegrationTest {

    @Test
    fun `un evenement deja credite ne rapporte pas deux fois`() {
        val deja = setOf("evt-1")
        val evenements = listOf(
            CalendrierIntegration.Evenement("evt-1", "Cours", 1_000L),
            CalendrierIntegration.Evenement("evt-2", "Dejeuner", 2_000L)
        )
        assertEquals(
            listOf("evt-2"),
            CalendrierIntegration.aCrediter(deja, evenements).map { it.id }
        )
    }

    @Test
    fun `les occurrences d un evenement recurrent ne comptent qu une fois`() {
        val deja = emptySet<String>()
        val evenements = listOf(
            CalendrierIntegration.Evenement("rec-1", "Cours de piano", 1_000L),
            CalendrierIntegration.Evenement("rec-1", "Cours de piano", 2_000L),
            CalendrierIntegration.Evenement("rec-2", "Sport", 3_000L)
        )
        assertEquals(
            listOf("rec-1", "rec-2"),
            CalendrierIntegration.aCrediter(deja, evenements).map { it.id }
        )
    }

    @Test
    fun `sans evenement rien n est a crediter`() {
        assertEquals(
            emptyList<CalendrierIntegration.Evenement>(),
            CalendrierIntegration.aCrediter(emptySet(), emptyList())
        )
    }
}
