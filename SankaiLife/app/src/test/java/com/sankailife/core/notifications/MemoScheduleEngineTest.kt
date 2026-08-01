package com.sankailife.core.notifications

import com.sankailife.core.data.db.entities.MemoProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MemoScheduleEngineTest {

    private val jour = LocalDate.of(2026, 8, 3)

    @Test
    fun `les creneaux sont stables pour un profil et un jour`() {
        val profil = profil()

        val premierCalcul = MemoScheduleEngine.creneauxAleatoires(profil, jour)
        val secondCalcul = MemoScheduleEngine.creneauxAleatoires(profil, jour)

        assertEquals(premierCalcul, secondCalcul)
    }

    @Test
    fun `les creneaux changent entre deux jours et deux profils`() {
        val profil = profil()
        val aujourdHui = MemoScheduleEngine.creneauxAleatoires(profil, jour)

        assertNotEquals(
            aujourdHui,
            MemoScheduleEngine.creneauxAleatoires(profil, jour.plusDays(1))
        )
        assertNotEquals(
            aujourdHui,
            MemoScheduleEngine.creneauxAleatoires(profil(id = profil.id + 1), jour)
        )
    }

    @Test
    fun `la frequence la plage et les trente minutes d ecart sont respectees`() {
        val creneaux = MemoScheduleEngine.creneauxAleatoires(profil(frequence = 6), jour)

        assertEquals(6, creneaux.size)
        assertTrue(creneaux.all { it in 9 * 60..21 * 60 })
        assertTrue(creneaux.zipWithNext().all { (avant, apres) -> apres - avant >= 30 })
    }

    @Test
    fun `une replanification retrouve le prochain creneau du meme tirage`() {
        val profil = profil(frequence = 6)
        val creneaux = MemoScheduleEngine.creneauxAleatoires(profil, jour)
        val maintenant = jour.atStartOfDay().plusMinutes(creneaux.first().toLong() + 1)

        val prochain = MemoScheduleEngine.prochainDeclenchement(
            profil,
            maintenant,
            QuietHours.DESACTIVE
        )

        assertEquals(jour, prochain?.toLocalDate())
        assertEquals(creneaux[1], prochain?.minuteDuJour())
    }

    @Test
    fun `un creneau en heures silencieuses est saute`() {
        val profil = profil(frequence = 6)
        val creneaux = MemoScheduleEngine.creneauxAleatoires(profil, jour)
        val premier = creneaux.first()
        val heuresSilencieuses = QuietHours(
            enabled = true,
            startMinute = premier,
            endMinute = premier + 1
        )

        val prochain = MemoScheduleEngine.prochainDeclenchement(
            profil,
            jour.atStartOfDay(),
            heuresSilencieuses
        )

        assertEquals(jour, prochain?.toLocalDate())
        assertEquals(creneaux[1], prochain?.minuteDuJour())
    }

    private fun profil(
        id: Long = 42,
        frequence: Int = 6
    ) = MemoProfileEntity(
        id = id,
        name = "Portugais",
        frequencyPerDay = frequence,
        isActive = true,
        randomMode = true,
        randomStartHour = 9,
        randomStartMinute = 0,
        randomEndHour = 21,
        randomEndMinute = 0,
        activeDays = "1,2,3,4,5,6,7"
    )

    private fun LocalDateTime.minuteDuJour(): Int = hour * 60 + minute
}
